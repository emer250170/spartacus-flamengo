package br.com.flamengo.matchday.payment.adyen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Chamadas à API de Checkout da Adyen (JSON por HTTPS, chave no cabeçalho X-API-Key), sem
 * biblioteca extra: java.net.http + Jackson da plataforma. A chave nunca vai para o log.
 */
public class AdyenClient
{
	private static final Logger LOG = LoggerFactory.getLogger(AdyenClient.class);
	private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<LinkedHashMap<String, Object>>()
	{
	};

	private final ObjectMapper mapper = new ObjectMapper();
	private volatile HttpClient http;

	public Map<String, Object> post(final String baseUrl, final String apiKey, final String path, final Map<String, Object> body,
			final int timeoutSeconds)
	{
		try
		{
			final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
					.timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json")
					.header("X-API-Key", apiKey).header("Idempotency-Key", UUID.randomUUID().toString())
					.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();
			return send(request, path);
		}
		catch (final IOException e)
		{
			throw new AdyenException("Não foi possível montar o pedido à Adyen.", 502);
		}
	}

	public Map<String, Object> get(final String baseUrl, final String apiKey, final String pathAndQuery, final int timeoutSeconds)
	{
		final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
				.timeout(Duration.ofSeconds(timeoutSeconds)).header("X-API-Key", apiKey).GET().build();
		return send(request, pathAndQuery.replaceAll("\\?.*", ""));
	}

	public Map<String, Object> read(final String json)
	{
		try
		{
			return json == null || json.isBlank() ? Collections.emptyMap() : mapper.readValue(json, MAP);
		}
		catch (final IOException e)
		{
			throw new AdyenException("Mensagem da Adyen ilegível.");
		}
	}

	public String write(final Object value)
	{
		try
		{
			return value == null ? null : mapper.writeValueAsString(value);
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	protected Map<String, Object> send(final HttpRequest request, final String path)
	{
		final HttpResponse<String> response;
		try
		{
			response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (final IOException e)
		{
			LOG.warn("Adyen {}: sem resposta ({})", path, e.getMessage());
			throw new AdyenException("A Adyen não respondeu. Tente de novo em instantes.", 502);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AdyenException("Chamada à Adyen interrompida.", 502);
		}
		final Map<String, Object> body = read(response.body());
		if (response.statusCode() / 100 != 2)
		{
			// {"status":422,"errorCode":"...","message":"...","errorType":"validation"}
			LOG.warn("Adyen {}: HTTP {} {} {}", path, response.statusCode(), body.get("errorCode"), body.get("message"));
			throw new AdyenException("A Adyen recusou a operação: " + (body.get("message") == null ? "HTTP " + response.statusCode()
					: body.get("message")), response.statusCode() == 401 || response.statusCode() == 403 ? 502 : 400);
		}
		return body;
	}

	protected HttpClient client()
	{
		if (http == null)
		{
			synchronized (this)
			{
				if (http == null)
				{
					http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
				}
			}
		}
		return http;
	}
}
