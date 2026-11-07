package br.com.flamengo.matchday.clearsale;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Chamadas JSON à API do ClearSale (java.net.http + Jackson da plataforma). Usuário, senha e token nunca
 * vão para o log.
 */
public class ClearSaleClient
{
	private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<LinkedHashMap<String, Object>>()
	{
	};

	private final ObjectMapper mapper = new ObjectMapper();
	private volatile HttpClient http;

	/** Resposta: código HTTP, corpo lido e o cabeçalho Request-ID */
	public static class Resposta
	{
		public final int status;
		public final Map<String, Object> corpo;
		public final String texto;
		public final String requestId;

		Resposta(final int status, final Map<String, Object> corpo, final String texto, final String requestId)
		{
			this.status = status;
			this.corpo = corpo;
			this.texto = texto;
			this.requestId = requestId;
		}

		public boolean ok()
		{
			return status >= 200 && status < 300;
		}
	}

	/**
	 * @param token null = sem Authorization (login)
	 * @throws IOException sem resposta (rede, tempo esgotado)
	 */
	public Resposta post(final String url, final String token, final Object body, final int timeoutMs) throws IOException
	{
		final HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs))
				.header("Content-Type", "application/json").header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
		if (token != null)
		{
			b.header("Authorization", "Bearer " + token);
		}
		final HttpResponse<String> r;
		try
		{
			r = client(timeoutMs).send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IOException("interrompido", e);
		}
		Map<String, Object> corpo = Collections.emptyMap();
		final String texto = r.body();
		try
		{
			if (texto != null && texto.trim().startsWith("{"))
			{
				corpo = mapper.readValue(texto, MAP);
			}
		}
		catch (final IOException e)
		{
			// corpo que não é JSON: fica só o texto
		}
		return new Resposta(r.statusCode(), corpo, texto, r.headers().firstValue("Request-ID").orElse(null));
	}

	protected HttpClient client(final int timeoutMs)
	{
		if (http == null)
		{
			synchronized (this)
			{
				if (http == null)
				{
					http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000))).build();
				}
			}
		}
		return http;
	}
}
