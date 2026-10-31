import com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Exercises actual upstream binaries; no Minecraft or transport doubles. */
public final class RedirectProbe {
    static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    static final String WRAPPER = "Lcom/llamalad7/mixinextras/wrapper/factory/FactoryRedirectWrapper;";

    public static final class FreshRedirect {
        @Redirect(method = "example", at = @At(value = "INVOKE", target = "Ljava/lang/Object;hashCode()I"))
        private static int redirect(Object target) { return target.hashCode(); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static ClassNode fixture(boolean list, String... kinds) {
        ClassNode node = new ClassNode();
        MethodNode method = new MethodNode();
        AnnotationNode redirect = new AnnotationNode(REDIRECT);
        ArrayList<AnnotationNode> points = new ArrayList<>();
        for (String kind : kinds) {
            AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
            at.visit("value", kind);
            points.add(at);
        }
        redirect.visit("at", list ? points : points.getFirst());
        method.visibleAnnotations = new ArrayList<>(List.of(redirect));
        node.methods.add(method);
        return node;
    }

    private static void transform(ClassNode node) {
        // The transformer only reads the ClassNode for these cases; IMixinInfo is unused.
        new FactoryRedirectWrapperMixinTransformer().transform(null, node);
    }

    public static void main(String[] args) throws Exception {
        boolean fixed = args[0].equals("0.5.5");
        VersionPredicate predicate = VersionPredicate.parse(">=0.19.4");
        check(!predicate.test(Version.parse("0.19.3")), "Older loader must be rejected");
        check(predicate.test(Version.parse("0.19.4")), "Requested loader must be accepted");
        check(predicate.test(Version.parse("0.19.5")), "Newer loader remains compatible");
        try (ZipFile jar = new ZipFile(args[1])) {
            String metadata = new String(jar.getInputStream(jar.getEntry("fabric.mod.json")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            check(metadata.contains("\">=0.19.4\""), "Built JAR must declare the requested loader minimum");
        }
        System.out.println("PASS actual Fabric version predicate accepts 0.19.4 and 0.19.5, rejects 0.19.3; JAR requirement checked");

        ClassNode compiled = new ClassNode();
        try (var input = RedirectProbe.class.getResourceAsStream("/RedirectProbe$FreshRedirect.class")) {
            new ClassReader(input).accept(compiled, 0);
        }
        MethodNode method = compiled.methods.stream().filter(m -> m.name.equals("redirect")).findFirst().orElseThrow();
        AnnotationNode redirect = method.visibleAnnotations.stream().filter(a -> a.desc.equals(REDIRECT)).findFirst().orElseThrow();
        Object at = redirect.values.get(redirect.values.indexOf("at") + 1);
        check(at instanceof ArrayList<?>, "Fabric Mixin 0.17.4 must compile @Redirect.at as an array");
        try {
            transform(compiled);
            check(fixed, "0.5.4 unexpectedly accepted array-shaped @Redirect");
            System.out.println("PASS MixinExtras 0.5.5 transforms @Redirect compiled against Fabric Mixin 0.17.4");
        } catch (ClassCastException e) {
            if (fixed || !e.getMessage().contains("AnnotationNode")) throw e;
            check(e.getStackTrace()[0].getClassName().endsWith("FactoryRedirectWrapperMixinTransformer"),
                    "Failure must originate in the reported transformer");
            System.out.println("PASS reproduced known 0.5.4 failure: " + e);
            System.out.println("at " + e.getStackTrace()[0]);
        }

        for (boolean list : new boolean[]{false, true}) {
            if (list && !fixed) continue;
            for (String kind : new String[]{"INVOKE", "NEW"}) {
                ClassNode node = fixture(list, kind);
                transform(node);
                check(node.methods.getFirst().visibleAnnotations.getFirst().desc.equals(
                        kind.equals("NEW") ? WRAPPER : REDIRECT), "Wrong transform for " + list + "/" + kind);
                System.out.println("PASS " + args[0] + " " + (list ? "array" : "single") + " @At " + kind);
            }
        }
        if (fixed) {
            ClassNode multiple = fixture(true, "INVOKE", "NEW");
            transform(multiple);
            check(multiple.methods.getFirst().visibleAnnotations.getFirst().desc.equals(WRAPPER),
                    "NEW later in the array must be wrapped");
            System.out.println("PASS array with multiple @At values");
        }
    }
}
