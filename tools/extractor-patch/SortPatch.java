import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
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
 * Rewrites {@code YoutubeChannelTabExtractor.getChannelTabsParameters} so
 * Videos Latest / Popular / Oldest use YouTube browse params from
 * {@code YoutubeChannelTabSortParams}.
 */
public final class SortPatch {

    private static final String TARGET =
            "org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeChannelTabExtractor.class";
    private static final String OWNER =
            "org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeChannelTabExtractor";
    private static final String HELPER_OWNER =
            "org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeChannelTabSortParams";

    public static void main(final String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: SortPatch <in.jar> <out.jar> <helper.class>");
            System.exit(2);
        }
        final Path inJar = Paths.get(args[0]);
        final Path outJar = Paths.get(args[1]);
        final Path helper = Paths.get(args[2]);
        boolean rewritten = false;
        try (JarFile jf = new JarFile(inJar.toFile());
             JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outJar))) {
            final Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                final JarEntry e = en.nextElement();
                jos.putNextEntry(new JarEntry(e.getName()));
                final byte[] raw = jf.getInputStream(e).readAllBytes();
                if (TARGET.equals(e.getName())) {
                    jos.write(rewriteExtractor(raw));
                    rewritten = true;
                } else {
                    jos.write(raw);
                }
                jos.closeEntry();
            }
            jos.putNextEntry(new JarEntry(HELPER_OWNER + ".class"));
            jos.write(Files.readAllBytes(helper));
            jos.closeEntry();
        }
        if (!rewritten) {
            throw new IllegalStateException("did not find " + TARGET);
        }
        System.out.println("rewrote getChannelTabsParameters + injected sort helper");
    }

    private static byte[] rewriteExtractor(final byte[] in) {
        final ClassReader cr = new ClassReader(in);
        final ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name,
                    final String descriptor, final String signature,
                    final String[] exceptions) {
                if ("getChannelTabsParameters".equals(name)
                        && "()Ljava/lang/String;".equals(descriptor)) {
                    final MethodVisitor mv = super.visitMethod(access, name, descriptor,
                            signature, exceptions);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OWNER,
                            "getName", "()Ljava/lang/String;", false);
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OWNER,
                            "getLinkHandler",
                            "()Lorg/schabi/newpipe/extractor/linkhandler/ListLinkHandler;",
                            false);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            "org/schabi/newpipe/extractor/linkhandler/ListLinkHandler",
                            "getSortFilter", "()Ljava/lang/String;", false);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER_OWNER, "params",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                            false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(2, 1);
                    mv.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return cw.toByteArray();
    }
}
