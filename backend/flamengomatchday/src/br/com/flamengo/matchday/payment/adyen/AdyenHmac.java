package br.com.flamengo.matchday.payment.adyen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


/**
 * Assinatura HMAC dos avisos do webhook da Adyen (HmacSHA256 com a chave em hexadecimal sobre
 * pspReference:originalReference:merchantAccountCode:merchantReference:value:currency:eventCode:success).
 */
public final class AdyenHmac
{
	private AdyenHmac()
	{
	}

	@SuppressWarnings("unchecked")
	public static boolean isValid(final Map<String, Object> item, final String hexKey)
	{
		if (item == null || hexKey == null || hexKey.isBlank())
		{
			return false;
		}
		final Object extra = item.get("additionalData");
		final Object received = extra instanceof Map ? ((Map<String, Object>) extra).get("hmacSignature") : null;
		if (!(received instanceof String))
		{
			return false;
		}
		final String expected = sign(signingString(item), hexKey);
		return expected != null
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), ((String) received).getBytes(StandardCharsets.UTF_8));
	}

	@SuppressWarnings("unchecked")
	public static String signingString(final Map<String, Object> item)
	{
		final Object amount = item.get("amount");
		final Map<String, Object> a = amount instanceof Map ? (Map<String, Object>) amount : Map.of();
		return String.join(":", s(item.get("pspReference")), s(item.get("originalReference")), s(item.get("merchantAccountCode")),
				s(item.get("merchantReference")), s(a.get("value")), s(a.get("currency")), s(item.get("eventCode")),
				s(item.get("success")));
	}

	public static String sign(final String data, final String hexKey)
	{
		try
		{
			final byte[] key = hex(hexKey.trim());
			final Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		}
		catch (final Exception e)
		{
			return null;
		}
	}

	private static byte[] hex(final String value)
	{
		final byte[] out = new byte[value.length() / 2];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
		}
		return out;
	}

	private static String s(final Object value)
	{
		return value == null ? "" : String.valueOf(value);
	}
}
