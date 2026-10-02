import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Makes {@code YoutubeChannelTabExtractor.getPage} accept YouTube's
 * {@code reloadContinuationItemsCommand} in addition to the legacy
 * {@code appendContinuationItemsAction}.
 *
 * <p>Channel Videos sort continuations (Popular / Oldest) are answered with a
 * reload command whose BODY slot carries the video lockups. Upstream 0.26.5
 * only looks for {@code appendContinuationItemsAction}, so those sorts loaded
 * an empty page.
 */
public final class ContinuationPatch {

    private static final String TARGET =
            "org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeChannelTabExtractor.class";
    private static final String JSON_OBJ = "com/grack/nanojson/JsonObject";
    private static final String RELOAD = "reloadContinuationItemsCommand";
    private static final String APPEND = "appendContinuationItemsAction";
    private static final String BODY_SLOT = "RELOAD_CONTINUATION_SLOT_BODY";

    public static void main(final String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: ContinuationPatch <in.jar> <out.jar>");
            System.exit(2);
        }
        final Path inJar = Paths.get(args[0]);
        final Path outJar = Paths.get(args[1]);
        boolean patched = false;
        try (JarFile jf = new JarFile(inJar.toFile());
             JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outJar))) {
            final Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                final JarEntry e = en.nextElement();
                jos.putNextEntry(new JarEntry(e.getName()));
                final byte[] raw = jf.getInputStream(e).readAllBytes();
                if (TARGET.equals(e.getName())) {
                    jos.write(rewrite(raw));
                    patched = true;
                } else {
                    jos.write(raw);
                }
                jos.closeEntry();
            }
        }
        if (!patched) {
            throw new IllegalStateException("did not find " + TARGET);
        }
        System.out.println("patched getPage lambdas for reloadContinuationItemsCommand");
    }

    private static byte[] rewrite(final byte[] in) {
        final ClassReader cr = new ClassReader(in);
        final ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name,
                    final String descriptor, final String signature,
                    final String[] exceptions) {
                if ("lambda$getPage$0".equals(name)
                        && "(Lcom/grack/nanojson/JsonObject;)Z".equals(descriptor)) {
                    final MethodVisitor mv = super.visitMethod(access, name, descriptor,
                            signature, exceptions);
                    writePredicate(mv);
                    return null;
                }
                if ("lambda$getPage$1".equals(name)
                        && "(Lcom/grack/nanojson/JsonObject;)Lcom/grack/nanojson/JsonObject;"
                                .equals(descriptor)) {
                    final MethodVisitor mv = super.visitMethod(access, name, descriptor,
                            signature, exceptions);
                    writeMap(mv);
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return cw.toByteArray();
    }

    private static void writePredicate(final MethodVisitor mv) {
        mv.visitCode();
        final Label trueL = new Label();
        final Label falseL = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(APPEND);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "has",
                "(Ljava/lang/String;)Z", false);
        mv.visitJumpInsn(Opcodes.IFNE, trueL);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(RELOAD);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "has",
                "(Ljava/lang/String;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, falseL);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(RELOAD);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "getObject",
                "(Ljava/lang/String;)Lcom/grack/nanojson/JsonObject;", false);
        mv.visitLdcInsn("slot");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "getString",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitLdcInsn(BODY_SLOT);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(Opcodes.IFNE, trueL);
        mv.visitLabel(falseL);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(trueL);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void writeMap(final MethodVisitor mv) {
        mv.visitCode();
        final Label reload = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(APPEND);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "has",
                "(Ljava/lang/String;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, reload);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(APPEND);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "getObject",
                "(Ljava/lang/String;)Lcom/grack/nanojson/JsonObject;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(reload);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitLdcInsn(RELOAD);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, JSON_OBJ, "getObject",
                "(Ljava/lang/String;)Lcom/grack/nanojson/JsonObject;", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }
}
