package p3.common;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class CryptoUtils {
	private static SecretKeySpec secretKey;
	
	private static void setKey(String myKey) {
        try {
            byte[] key = myKey.getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	public static String encriptar(String strToEncrypt, String secret) {
        try {
            if (secret == null) {
            	return null;
            }
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")));
        } 
        catch (Exception e) {
            System.err.println("Error al encriptar: " + e.toString());
        }
        return null;
    }
	
	public static String desencriptar(String strToDecrypt, String secret) {
        try {
            if (secret == null) {
            	return null;
            }
            setKey(secret);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } 
        catch (Exception e) {
            //la clave no coincide o el mensaje está corrupto
            System.err.println("Error al desencriptar: " + e.toString()); 
        }
        return null;
    }
	
	public static PrivateKey cargarClavePrivada(String rutaFichero) throws Exception {
        String keyContent = new String(Files.readAllBytes(Paths.get(rutaFichero)));
        keyContent = keyContent.replace("-----BEGIN PRIVATE KEY-----", "")
                               .replace("-----END PRIVATE KEY-----", "")
                               .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }
	
	public static String firmarRSA(String datos, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(datos.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } 
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
	
	public static X509Certificate cargarCertificadoDesdeString(String certStr) throws Exception {
        if(!certStr.contains("BEGIN CERTIFICATE")) {
             certStr = "-----BEGIN CERTIFICATE-----\n" + certStr + "\n-----END CERTIFICATE-----";
        }
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        return (X509Certificate) fact.generateCertificate(
        		new ByteArrayInputStream(certStr.getBytes(StandardCharsets.UTF_8))
        );
    }
	
	public static boolean verificarFirmaRSA(String datos, String firmaBase64, X509Certificate cert) {
        try {
            PublicKey publicKey = cert.getPublicKey();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(datos.getBytes(StandardCharsets.UTF_8));
            
            byte[] firmaBytes = Base64.getDecoder().decode(firmaBase64);
            return signature.verify(firmaBytes);
        } 
        catch (Exception e) {
            System.err.println("Error verificando firma RSA: " + e.getMessage());
            return false;
        }
    }
}
