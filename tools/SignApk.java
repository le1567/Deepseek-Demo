import com.android.apksig.ApkSigner;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** 用 apksig 库在内存中签名 APK（规避 JDK25 下 apksigner 命令行的大文件 EIO 问题） */
public class SignApk {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: SignApk <keystore> <storepass> <alias> <keypass> <input.apk> [output.apk]");
            System.exit(2);
        }
        String ksPath = args[0], storePass = args[1], alias = args[2], keyPass = args[3];
        String input = args[4], output = args.length > 5 ? args[5] : input + ".signed.apk";

        KeyStore ks = KeyStore.getInstance("JKS");
        try (java.io.InputStream in = Files.newInputStream(Paths.get(ksPath))) {
            ks.load(in, storePass.toCharArray());
        }
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
        if (chain != null) {
            for (java.security.cert.Certificate c : chain) certs.add((X509Certificate) c);
        }

        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                "CERT", privateKey, certs).build();
        List<ApkSigner.SignerConfig> signers = new ArrayList<ApkSigner.SignerConfig>();
        signers.add(signerConfig);

        byte[] apkBytes = Files.readAllBytes(Paths.get(input));
        DataSource ds = DataSources.asDataSource(java.nio.ByteBuffer.wrap(apkBytes));
        ApkSigner signer = new ApkSigner.Builder(signers)
                .setInputApk(ds)
                .setOutputApk(Paths.get(output).toFile())
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build();
        signer.sign();
        System.out.println("SIGNED: " + output + " (" + Files.size(Paths.get(output)) + " bytes)");
    }
}
