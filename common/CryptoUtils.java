package p3.common;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

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
}
