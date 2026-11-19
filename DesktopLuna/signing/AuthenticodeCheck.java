// Reads back the Authenticode signature of each Windows program or library named, for Luna DE's
// packaging (signing/windows.js), on any machine: Windows' own tools to do this run only there.
// It runs on the jsign jar the build fetches (`java -cp jsign.jar AuthenticodeCheck.java FILE...`)
// and prints one line of JSON a file:
//
//   {"file":…, "valid":true|false, "timestamped":true|false, "subject":"CN=…", "problem":…}
//
// "valid" means the file is unchanged since it was signed (the digest in the signature is the
// file's own) and the signature verifies against the certificate it names. Whether Windows trusts
// that certificate's issuer is Windows' to say, and isn't checked here.

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;

import net.jsign.DigestAlgorithm;
import net.jsign.Signable;
import net.jsign.bouncycastle.asn1.ASN1ObjectIdentifier;
import net.jsign.bouncycastle.asn1.ASN1OctetString;
import net.jsign.bouncycastle.asn1.ASN1Sequence;
import net.jsign.bouncycastle.asn1.cms.AttributeTable;
import net.jsign.bouncycastle.asn1.cms.ContentInfo;
import net.jsign.bouncycastle.asn1.cms.SignedData;
import net.jsign.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import net.jsign.bouncycastle.asn1.x509.AlgorithmIdentifier;
import net.jsign.bouncycastle.cert.X509CertificateHolder;
import net.jsign.bouncycastle.cms.CMSSignedData;
import net.jsign.bouncycastle.cms.SignerInformation;
import net.jsign.bouncycastle.cms.jcajce.JcaSignerInfoVerifierBuilder;
import net.jsign.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public class AuthenticodeCheck {
    /** Microsoft's RFC 3161 timestamp, an unsigned attribute of the signer. */
    private static final ASN1ObjectIdentifier RFC3161 = new ASN1ObjectIdentifier("1.3.6.1.4.1.311.3.3.1");

    public static void main(String[] args) {
        for (String a : args) {
            System.out.println(check(new File(a)));
        }
    }

    static String check(File file) {
        try (Signable signable = Signable.of(file)) {
            List<CMSSignedData> signatures = signable.getSignatures();
            if (signatures.isEmpty()) {
                return json(file, false, false, null, "unsigned");
            }
            CMSSignedData signed = signatures.get(0);
            SignerInformation signer = signed.getSignerInfos().getSigners().iterator().next();
            X509CertificateHolder cert = (X509CertificateHolder) signed.getCertificates().getMatches(signer.getSID()).iterator().next();
            String subject = cert.getSubject().toString();
            AttributeTable unsigned = signer.getUnsignedAttributes();
            boolean timestamped = unsigned != null
                    && (unsigned.get(RFC3161) != null || unsigned.get(PKCSObjectIdentifiers.pkcs_9_at_counterSignature) != null);

            // The file's digest as the signature holds it (SpcIndirectDataContent's DigestInfo), against the file's own.
            ContentInfo info = signed.toASN1Structure();
            ASN1Sequence indirect = ASN1Sequence.getInstance(SignedData.getInstance(info.getContent()).getEncapContentInfo().getContent());
            // DigestInfo ::= SEQUENCE { AlgorithmIdentifier, OCTET STRING }, read by hand: jsign's
            // trimmed BouncyCastle leaves out DigestInfo's own accessors.
            ASN1Sequence held = ASN1Sequence.getInstance(indirect.getObjectAt(1));
            DigestAlgorithm algorithm = DigestAlgorithm.of(AlgorithmIdentifier.getInstance(held.getObjectAt(0)).getAlgorithm());
            byte[] digest = ASN1OctetString.getInstance(held.getObjectAt(1)).getOctets();
            if (algorithm == null || !Arrays.equals(signable.computeDigest(algorithm), digest)) {
                return json(file, false, timestamped, subject, "changed since it was signed");
            }
            PublicKey publicKey = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(cert.toASN1Structure().getEncoded())).getPublicKey();
            // The signer signed a digest of the indirect data jsign rebuilds from the file itself
            // (the signature as read carries none of its own content to hash).
            CMSSignedData withContent = new CMSSignedData(signable.createSignedContent(algorithm), signed.toASN1Structure());
            SignerInformation again = withContent.getSignerInfos().getSigners().iterator().next();
            boolean verifies = again.verify(new JcaSignerInfoVerifierBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(publicKey));
            return json(file, verifies, timestamped, subject, verifies ? null : "the signature doesn't verify");
        } catch (Exception e) {
            return json(file, false, false, null, "unreadable signature: " + e);
        }
    }

    private static String json(File file, boolean valid, boolean timestamped, String subject, String problem) {
        return "{\"file\":" + str(file.getPath()) + ",\"valid\":" + valid + ",\"timestamped\":" + timestamped
                + ",\"subject\":" + str(subject) + ",\"problem\":" + str(problem) + "}";
    }

    private static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') { b.append('\\').append(c); }
            else if (c < 0x20) { b.append(String.format("\\u%04x", (int) c)); }
            else { b.append(c); }
        }
        return b.append('"').toString();
    }
}
