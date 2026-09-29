/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import zone.rong.clearskies.api.Diagnostic;
import zone.rong.clearskies.api.ExpandClasspath;
import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.LanguageLevel;
import zone.rong.clearskies.api.StarExpander;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTreePathScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Attributes a file with javac and splices only the physical lines of on-demand imports.
 */
final class DefaultStarExpander implements StarExpander {

    private final ExpandClasspath classpath;
    private final LanguageLevel languageLevel;
    private final Charset encoding;
    private final Object pathLock = new Object();
    private List<java.nio.file.Path> resolvedEntries;
    private List<java.nio.file.Path> resolvedRoots;
    private List<String> pathErrors;

    DefaultStarExpander(ExpandClasspath classpath, LanguageLevel languageLevel, Charset encoding) {
        this.classpath = classpath;
        this.languageLevel = languageLevel;
        this.encoding = encoding;
    }

    @Override
    public ExpandClasspath classpath() {
        return classpath;
    }

    @Override
    public LanguageLevel languageLevel() {
        return languageLevel;
    }

    @Override
    public ExpandResult expand(ExpandRequest request) {
        String source = request.source();
        if (ImportRegion.skip(source, request.name())) {
            return ExpandResult.unchanged(source);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return ExpandResult.failed(
                source,
                List.of(Diagnostic.error("JDK compiler is required (javax.tools.JavaCompiler is unavailable). " + "Run ClearSkies on a JDK, not a JRE."))
            );
        }
        List<String> resolvedErrors = ensurePaths();
        if (!resolvedErrors.isEmpty()) {
            List<Diagnostic> diagnostics = new ArrayList<>(resolvedErrors.size());
            for (String error : resolvedErrors) {
                diagnostics.add(Diagnostic.error(error));
            }
            return ExpandResult.failed(source, diagnostics);
        }
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(collector, null, encoding)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, resolvedEntries);
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, resolvedRoots);
            JavaFileObject file = new SourceFileObject(request);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, collector, compilerOptions(), null, List.of(file));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            List<Diagnostic> fatal = fatalDiagnostics(collector);
            if (!fatal.isEmpty()) {
                return ExpandResult.failed(source, fatal);
            }
            CompilationUnitTree unit = first(units);
            if (unit == null) {
                return ExpandResult.failed(source, List.of(Diagnostic.error("failed to parse " + request.name())));
            }
            task.analyze();
            fatal = fatalDiagnostics(collector);
            if (!fatal.isEmpty()) {
                return ExpandResult.failed(source, fatal);
            }
            return expandUnit(source, request.name(), unit, task, collector);
        } catch (IOException e) {
            return ExpandResult.failed(source, List.of(Diagnostic.error("cannot attribute " + request.name() + ": " + e.getMessage())));
        } catch (RuntimeException e) {
            return ExpandResult.failed(source, List.of(Diagnostic.error("cannot attribute " + request.name() + ": " + e.getMessage())));
        }
    }

    private List<String> ensurePaths() {
        synchronized (pathLock) {
            if (pathErrors == null) {
                ClasspathEntries.Resolution entries = ClasspathEntries.classpath(classpath.entries());
                ClasspathEntries.Resolution roots = ClasspathEntries.sourceRoots(classpath.sourceRoots());
                List<String> errors = new ArrayList<>();
                errors.addAll(entries.errors());
                errors.addAll(roots.errors());
                this.resolvedEntries = entries.paths();
                this.resolvedRoots = roots.paths();
                this.pathErrors = List.copyOf(errors);
            }
            return pathErrors;
        }
    }

    private ExpandResult expandUnit(String source, String name, CompilationUnitTree unit, JavacTask task, DiagnosticCollector<JavaFileObject> collector) {
        Trees trees = Trees.instance(task);
        List<StarImport> stars = new ArrayList<>();
        Set<String> singleTypeSimpleNames = new HashSet<>();
        Set<String> singleStaticSimpleNames = new HashSet<>();
        Set<StarImport> frozen = new HashSet<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ImportTree importTree : unit.getImports()) {
            Tree qualified = importTree.getQualifiedIdentifier();
            if (!(qualified instanceof MemberSelectTree select) || !select.getIdentifier().contentEquals("*")) {
                String fqn = qualifiedName(qualified);
                int lastDot = fqn.lastIndexOf('.');
                String simpleName = lastDot < 0 ? fqn : fqn.substring(lastDot + 1);
                if (importTree.isStatic()) {
                    singleStaticSimpleNames.add(simpleName);
                    Element imported = trees.getElement(trees.getPath(unit, qualified));
                    if (imported instanceof TypeElement) {
                        singleTypeSimpleNames.add(simpleName);
                    }
                } else {
                    singleTypeSimpleNames.add(simpleName);
                }
                continue;
            }
            Tree ownerTree = select.getExpression();
            Element owner = ownerOf(trees, task, unit, ownerTree);
            long start = trees.getSourcePositions().getStartPosition(unit, importTree);
            long end = trees.getSourcePositions().getEndPosition(unit, importTree);
            if (start < 0 || end < 0) {
                continue;
            }
            int line = unit.getLineMap() == null ? 0 : (int) unit.getLineMap().getLineNumber(start);
            String ownerName = qualifiedName(ownerTree);
            StarImport star = new StarImport(LineSpan.of(source, (int) start, (int) end), owner, ownerName, line, importTree.isStatic(), task.getElements());
            stars.add(star);
            if (isUnusualImport(source, (int) start, (int) end, ownerName, importTree.isStatic())) {
                frozen.add(star);
                diagnostics.add(Diagnostic.warning("left unusually formatted import " + ownerName + ".* unchanged", line, 1));
            }
        }
        if (stars.isEmpty()) {
            return ExpandResult.unchanged(source);
        }

        String packageName = qualifiedName(unit.getPackageName());
        UseScanner uses = new UseScanner(trees, DocTrees.instance(task));
        uses.scan(unit, null);
        uses.unresolved.addAll(ambiguousNames(source, collector));
        LinkedHashMap<StarImport, LinkedHashSet<Element>> assigned = new LinkedHashMap<>();
        for (StarImport star : stars) {
            assigned.put(star, new LinkedHashSet<>());
        }

        assign(uses.used, trees, unit, packageName, singleTypeSimpleNames, singleStaticSimpleNames, stars, assigned, frozen, diagnostics);
        assignUnresolved(uses.unresolved, packageName, singleTypeSimpleNames, singleStaticSimpleNames, stars, assigned, frozen, diagnostics);

        boolean unresolved = hasUnresolvedSymbols(collector);
        StringBuilder out = new StringBuilder(source);
        boolean changed = false;
        boolean incomplete = !frozen.isEmpty();
        List<StarImport> bottomUp = new ArrayList<>(stars);
        bottomUp.sort(Comparator.comparingInt((StarImport star) -> star.span.start).reversed());
        for (StarImport star : bottomUp) {
            if (frozen.contains(star)) {
                incomplete = true;
                continue;
            }
            List<String> fqns = importNames(star, assigned.get(star));
            String next;
            if (fqns.isEmpty()) {
                if (unresolved) {
                    diagnostics.add(
                        Diagnostic.warning("left unused-looking star import " + star.ownerName + ".* because the file has unresolved types", star.line, 1)
                    );
                    incomplete = true;
                    continue;
                }
                if (!star.span.trailingComment.isEmpty()) {
                    diagnostics.add(
                        Diagnostic.warning("left unused star import " + star.ownerName + ".* unchanged because it has a trailing comment", star.line, 1)
                    );
                    incomplete = true;
                    continue;
                }
                next = "";
            } else {
                next = star.span.replacement(fqns, star.staticImport);
            }
            String original = source.substring(star.span.start, star.span.end);
            if (next.equals(original)) {
                continue;
            }
            out.replace(star.span.start, star.span.end, next);
            changed = true;
        }
        String text = changed ? out.toString() : source;
        if (incomplete) {
            return ExpandResult.incomplete(source, text, diagnostics);
        }
        if (!changed) {
            return ExpandResult.unchanged(source).withDiagnostics(diagnostics);
        }
        return ExpandResult.expanded(source, text).withDiagnostics(diagnostics);
    }

    private List<String> compilerOptions() {
        return List.of("--release", Integer.toString(languageLevel.release()), "-proc:none", "-Xlint:none");
    }

    private static CompilationUnitTree first(Iterable<? extends CompilationUnitTree> units) {
        for (CompilationUnitTree unit : units) {
            return unit;
        }
        return null;
    }

    private static List<Diagnostic> fatalDiagnostics(DiagnosticCollector<JavaFileObject> collector) {
        List<Diagnostic> fatal = new ArrayList<>();
        for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
            if (diagnostic.getKind() != javax.tools.Diagnostic.Kind.ERROR) {
                continue;
            }
            if (isUnresolvedSymbol(diagnostic.getCode())) {
                continue;
            }
            int line = diagnostic.getLineNumber() == javax.tools.Diagnostic.NOPOS ? 0 : (int) diagnostic.getLineNumber();
            int column = diagnostic.getColumnNumber() == javax.tools.Diagnostic.NOPOS ? 0 : (int) diagnostic.getColumnNumber();
            String message = diagnostic.getMessage(null);
            fatal.add(Diagnostic.error(message == null ? diagnostic.getCode() : message, line, column));
        }
        return fatal;
    }

    private static boolean hasUnresolvedSymbols(DiagnosticCollector<JavaFileObject> collector) {
        for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
            if (diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR && isUnresolvedSymbol(diagnostic.getCode())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> ambiguousNames(String source, DiagnosticCollector<JavaFileObject> collector) {
        Set<String> names = new LinkedHashSet<>();
        for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
            if (diagnostic.getKind() != javax.tools.Diagnostic.Kind.ERROR || diagnostic.getCode() == null || !diagnostic.getCode().contains("ambiguous")) {
                continue;
            }
            long start = diagnostic.getStartPosition();
            long end = diagnostic.getEndPosition();
            if (start < 0 || end <= start || end > source.length()) {
                continue;
            }
            String name = source.substring((int) start, (int) end);
            if (Character.isJavaIdentifierStart(name.charAt(0)) && name.chars().allMatch(Character::isJavaIdentifierPart)) {
                names.add(name);
            }
        }
        return names;
    }

    static boolean isUnresolvedSymbol(String code) {
        if (code == null) {
            return false;
        }
        return code.contains("cant.resolve") ||
            code.contains("cant.find.symbol") ||
            code.endsWith("doesnt.exist") ||
            code.contains("package.does.not.exist") ||
            code.contains("ambiguous");
    }

    static boolean isUnusualImport(String source, int declStart, int declEnd, String ownerName, boolean staticImport) {
        if (declStart < 0 || declEnd < declStart || declEnd > source.length()) {
            return true;
        }
        String decl = UnicodeEscapes.translate(source.substring(declStart, declEnd));
        if (decl.contains("/*") || decl.contains("//") || decl.indexOf('\n') >= 0 || decl.indexOf('\r') >= 0) {
            return true;
        }
        String compact = decl.replaceAll("\\s+", " ").strip();
        return !compact.equals("import " + (staticImport ? "static " : "") + ownerName + ".*;");
    }

    private static Element ownerOf(Trees trees, JavacTask task, CompilationUnitTree unit, Tree ownerTree) {
        Element owner = trees.getElement(trees.getPath(unit, ownerTree));
        if (owner != null) {
            return owner;
        }
        String name = qualifiedName(ownerTree);
        if (name.isEmpty()) {
            return null;
        }
        PackageElement pkg = task.getElements().getPackageElement(name);
        if (pkg != null) {
            return pkg;
        }
        return task.getElements().getTypeElement(name);
    }

    private static void assign(
        Set<Element> used,
        Trees trees,
        CompilationUnitTree unit,
        String packageName,
        Set<String> singleTypeSimpleNames,
        Set<String> singleStaticSimpleNames,
        List<StarImport> stars,
        LinkedHashMap<StarImport, LinkedHashSet<Element>> assigned,
        Set<StarImport> frozen,
        List<Diagnostic> diagnostics
    ) {
        for (Element element : used) {
            recordUse(element, trees, unit, packageName, singleTypeSimpleNames, singleStaticSimpleNames, stars, assigned, frozen, diagnostics);
        }
    }

    private static void assignUnresolved(
        Set<String> unresolved,
        String packageName,
        Set<String> singleTypeSimpleNames,
        Set<String> singleStaticSimpleNames,
        List<StarImport> stars,
        LinkedHashMap<StarImport, LinkedHashSet<Element>> assigned,
        Set<StarImport> frozen,
        List<Diagnostic> diagnostics
    ) {
        for (String simple : unresolved) {
            if (singleTypeSimpleNames.contains(simple) || singleStaticSimpleNames.contains(simple)) {
                continue;
            }
            List<StarImport> owners = new ArrayList<>();
            List<Element> members = new ArrayList<>();
            for (StarImport star : stars) {
                Element member = star.memberNamed(simple);
                if (member != null) {
                    owners.add(star);
                    members.add(member);
                }
            }
            owners = uniqueOwners(owners);
            if (owners.size() == 1) {
                recordUse(members.get(0), null, null, packageName, singleTypeSimpleNames, singleStaticSimpleNames, stars, assigned, frozen, diagnostics);
            } else if (owners.size() > 1) {
                frozen.addAll(owners);
                diagnostics.add(
                    Diagnostic.error(simple + " is ambiguous between " + names(owners, simple) + "; left those star imports unchanged", owners.get(0).line, 1)
                );
            }
        }
    }

    private static void recordUse(
        Element element,
        Trees trees,
        CompilationUnitTree unit,
        String packageName,
        Set<String> singleTypeSimpleNames,
        Set<String> singleStaticSimpleNames,
        List<StarImport> stars,
        LinkedHashMap<StarImport, LinkedHashSet<Element>> assigned,
        Set<StarImport> frozen,
        List<Diagnostic> diagnostics
    ) {
        if (trees != null && unit != null && declaredIn(trees, unit, element)) {
            return;
        }
        String simple = element.getSimpleName().toString();
        if (element instanceof TypeElement type) {
            if (isJavaLangTopLevel(type) || isTopLevelIn(type, packageName) || singleTypeSimpleNames.contains(simple)) {
                return;
            }
        } else if (!isStaticImportable(element) || singleStaticSimpleNames.contains(simple)) {
            return;
        }
        List<StarImport> owners = new ArrayList<>();
        for (StarImport star : stars) {
            if (star.provides(element)) {
                owners.add(star);
            }
        }
        owners = uniqueOwners(owners);
        if (owners.size() == 1) {
            assigned.get(owners.get(0)).add(element);
        } else if (owners.size() > 1) {
            frozen.addAll(owners);
            diagnostics.add(
                Diagnostic.error(simple + " is ambiguous between " + names(owners, simple) + "; left those star imports unchanged", owners.get(0).line, 1)
            );
        }
    }

    private static List<StarImport> uniqueOwners(List<StarImport> owners) {
        if (owners.size() < 2) {
            return owners;
        }
        List<StarImport> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (StarImport owner : owners) {
            if (seen.add(owner.identity())) {
                unique.add(owner);
            }
        }
        return unique;
    }

    private static boolean declaredIn(Trees trees, CompilationUnitTree unit, Element element) {
        TreePath path = trees.getPath(element);
        return path != null && path.getCompilationUnit() == unit;
    }

    private static boolean isStaticImportable(Element element) {
        ElementKind kind = element.getKind();
        return element.getModifiers().contains(Modifier.STATIC) &&
            (kind == ElementKind.FIELD || kind == ElementKind.ENUM_CONSTANT || kind == ElementKind.METHOD || kind.isClass() || kind.isInterface());
    }

    private static boolean isJavaLangTopLevel(TypeElement type) {
        return type.getNestingKind() == NestingKind.TOP_LEVEL &&
            type.getEnclosingElement() instanceof PackageElement pkg &&
            pkg.getQualifiedName().contentEquals("java.lang");
    }

    private static boolean isTopLevelIn(TypeElement type, String packageName) {
        if (type.getNestingKind() != NestingKind.TOP_LEVEL) {
            return false;
        }
        if (!(type.getEnclosingElement() instanceof PackageElement pkg)) {
            return false;
        }
        return pkg.getQualifiedName().contentEquals(packageName);
    }

    private static List<String> importNames(StarImport star, Set<Element> elements) {
        Set<String> unique = new HashSet<>();
        for (Element element : elements) {
            if (star.staticImport) {
                unique.add(star.ownerName + "." + element.getSimpleName());
            } else if (element instanceof TypeElement type) {
                unique.add(type.getQualifiedName().toString());
            }
        }
        List<String> names = new ArrayList<>(unique);
        names.sort(String::compareTo);
        return names;
    }

    private static String names(List<StarImport> owners, String simpleName) {
        List<String> parts = new ArrayList<>(owners.size());
        for (StarImport owner : owners) {
            parts.add(owner.ownerName + "." + simpleName);
        }
        return String.join(" and ", parts);
    }

    private static String qualifiedName(Tree tree) {
        if (tree == null) {
            return "";
        }
        if (tree instanceof IdentifierTree identifier) {
            return identifier.getName().toString();
        }
        if (tree instanceof MemberSelectTree select) {
            String parent = qualifiedName(select.getExpression());
            return parent.isEmpty() ? select.getIdentifier().toString() : parent + "." + select.getIdentifier();
        }
        return "";
    }

    private static final class StarImport {

        final LineSpan span;
        final Element owner;
        final String ownerName;
        final int line;
        final boolean staticImport;
        final Elements elements;

        StarImport(LineSpan span, Element owner, String ownerName, int line, boolean staticImport, Elements elements) {
            this.span = span;
            this.owner = owner;
            this.ownerName = ownerName;
            this.line = line;
            this.staticImport = staticImport;
            this.elements = elements;
        }

        String identity() {
            if (owner instanceof PackageElement pkg) {
                return "pkg:" + pkg.getQualifiedName();
            }
            if (owner instanceof TypeElement type) {
                return "type:" + type.getQualifiedName();
            }
            return "name:" + ownerName;
        }

        boolean provides(Element element) {
            if (staticImport) {
                if (!(owner instanceof TypeElement outer) || !isStaticImportable(element)) {
                    return false;
                }
                for (Element member : elements.getAllMembers(outer)) {
                    if (member.equals(element)) {
                        return true;
                    }
                }
                return false;
            }
            if (!(element instanceof TypeElement type)) {
                return false;
            }
            if (owner instanceof PackageElement pkg) {
                return type.getNestingKind() == NestingKind.TOP_LEVEL && pkg.equals(type.getEnclosingElement());
            }
            if (owner instanceof TypeElement outer) {
                return outer.equals(type.getEnclosingElement());
            }
            String qualified = type.getQualifiedName().toString();
            String prefix = ownerName + ".";
            if (!qualified.startsWith(prefix)) {
                return false;
            }
            String rest = qualified.substring(prefix.length());
            return !rest.isEmpty() && rest.indexOf('.') < 0;
        }

        Element memberNamed(String simple) {
            if (staticImport) {
                if (!(owner instanceof TypeElement outer)) {
                    return null;
                }
                for (Element member : elements.getAllMembers(outer)) {
                    if (member.getSimpleName().contentEquals(simple) && isStaticImportable(member)) {
                        return member;
                    }
                }
                return null;
            }
            TypeElement type = elements.getTypeElement(ownerName + "." + simple);
            if (type == null) {
                return null;
            }
            if (owner instanceof TypeElement) {
                return owner.equals(type.getEnclosingElement()) ? type : null;
            }
            return type.getNestingKind() == NestingKind.TOP_LEVEL ? type : null;
        }

    }

    private static final class UseScanner extends TreePathScanner<Void, Void> {

        private final Trees trees;
        private final DocTrees docTrees;
        private final Set<Element> used = new LinkedHashSet<>();
        private final Set<String> unresolved = new LinkedHashSet<>();

        UseScanner(Trees trees, DocTrees docTrees) {
            this.trees = trees;
            this.docTrees = docTrees;
        }

        @Override
        public Void visitCompilationUnit(CompilationUnitTree node, Void unused) {
            scanDoc();
            return super.visitCompilationUnit(node, unused);
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            scanDoc();
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            scanDoc();
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            scanDoc();
            return super.visitVariable(node, unused);
        }

        @Override
        public Void visitImport(ImportTree node, Void unused) {
            return null;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            scan(node.getExpression(), unused);
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type && isNamedType(type) && type.getSimpleName().contentEquals(node.getName())) {
                if (type.asType().getKind() == TypeKind.ERROR) {
                    unresolved.add(node.getName().toString());
                } else {
                    used.add(type);
                }
            } else if (element != null && element.getSimpleName().contentEquals(node.getName()) && isStaticImportable(element)) {
                used.add(element);
            } else if (element == null || looksLikeTypeName(node.getName().toString())) {
                unresolved.add(node.getName().toString());
            }
            return super.visitIdentifier(node, unused);
        }

        private void scanDoc() {
            TreePath path = getCurrentPath();
            DocCommentTree comment = docTrees.getDocCommentTree(path);
            if (comment == null) {
                return;
            }
            new DocUseScanner().scan(new DocTreePath(path, comment), null);
        }

        private static boolean looksLikeTypeName(String name) {
            return !name.isEmpty() && Character.isUpperCase(name.charAt(0));
        }

        private static boolean isNamedType(TypeElement type) {
            ElementKind kind = type.getKind();
            return kind.isClass() || kind.isInterface() || kind == ElementKind.ENUM || kind == ElementKind.RECORD;
        }

        private final class DocUseScanner extends DocTreePathScanner<Void, Void> {

            @Override
            public Void visitLink(LinkTree node, Void unused) {
                scan(node.getReference(), unused);
                return null;
            }

            @Override
            public Void visitSee(SeeTree node, Void unused) {
                for (DocTree tree : node.getReference()) {
                    if (tree instanceof ReferenceTree) {
                        scan(tree, unused);
                    }
                }
                return null;
            }

            @Override
            public Void visitThrows(ThrowsTree node, Void unused) {
                scan(node.getExceptionName(), unused);
                return null;
            }

            @Override
            public Void visitValue(ValueTree node, Void unused) {
                scan(node.getReference(), unused);
                return null;
            }

            @Override
            public Void visitReference(ReferenceTree node, Void unused) {
                Element element = docTrees.getElement(getCurrentPath());
                if (element instanceof TypeElement type && isNamedType(type)) {
                    used.add(type);
                } else if (element instanceof ExecutableElement executable && executable.getEnclosingElement() instanceof TypeElement type) {
                    used.add(type);
                } else if (element instanceof VariableElement variable && variable.getEnclosingElement() instanceof TypeElement type) {
                    used.add(type);
                } else {
                    String signature = node.getSignature();
                    if (signature != null) {
                        int hash = signature.indexOf('#');
                        String typePart = hash >= 0 ? signature.substring(0, hash) : signature;
                        int lastDot = typePart.lastIndexOf('.');
                        String simple = lastDot >= 0 ? typePart.substring(lastDot + 1) : typePart;
                        if (looksLikeTypeName(simple)) {
                            unresolved.add(simple);
                        }
                    }
                }
                return super.visitReference(node, unused);
            }

        }

    }

    private static final class SourceFileObject extends SimpleJavaFileObject {

        private final String source;

        SourceFileObject(ExpandRequest request) {
            super(uriFor(request.name()), JavaFileObject.Kind.SOURCE);
            this.source = request.source();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        private static URI uriFor(String name) {
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            String file = slash >= 0 ? name.substring(slash + 1) : name;
            if (!file.endsWith(".java")) {
                file = "Source.java";
            }
            try {
                return new URI("string", "", "/" + file, null);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("cannot encode source name '" + file + "'", e);
            }
        }

    }

}
