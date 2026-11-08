package br.com.flamengo.matchday.ia.impl;

import br.com.flamengo.matchday.ia.TraducaoService;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Tradução pela API de mensagens da Anthropic (Claude). Uma chamada por clique, com instrução fixa de tradutor de loja
 * de futebol: devolve só o texto, sem aspas nem comentário, mantendo nomes próprios (Flamengo, Maracanã, Nação).
 * A chave nunca vai para log nem para a mensagem de erro.
 */
public class AnthropicTraducaoService implements TraducaoService
{
	private static final Logger LOG = LoggerFactory.getLogger(AnthropicTraducaoService.class);
	private static final int MAX_CHARS = 4000;

	private final ObjectMapper mapper = new ObjectMapper();
	private volatile HttpClient http;
	private ConfigurationService configurationService;

	@Override
	public boolean disponivel()
	{
		return configurationService.getConfiguration().getBoolean("flamengo.ia.traducao.ativo", true)
				&& StringUtils.isNotBlank(config("flamengo.ia.traducao.chave", null));
	}

	@Override
	public String traduzir(final String texto, final String de, final String para)
	{
		if (StringUtils.isBlank(texto))
		{
			return "";
		}
		if (!disponivel())
		{
			throw new IllegalStateException("A tradução com IA está desligada ou sem chave configurada no servidor.");
		}
		if (texto.length() > MAX_CHARS)
		{
			throw new IllegalStateException("Texto longo demais para traduzir de uma vez (máximo " + MAX_CHARS + " caracteres).");
		}
		final Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", config("flamengo.ia.traducao.modelo", "claude-haiku-5-5"));
		body.put("max_tokens", Integer.valueOf(1024));
		body.put("system", "Você traduz textos da loja oficial do Clube de Regatas do Flamengo (nomes e descrições de produtos) de "
				+ nome(de) + " para " + nome(para) + ". Traduza com naturalidade, no tom de uma loja de futebol. Mantenha nomes "
				+ "próprios como Flamengo, Mengão, Maracanã, Nação e nomes de jogadores. Mantenha números, medidas e códigos. "
				+ "Responda somente com o texto traduzido, sem aspas, sem explicação e sem marcação.");
		final List<Map<String, Object>> messages = new ArrayList<>();
		final Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "user");
		message.put("content", texto);
		messages.add(message);
		body.put("messages", messages);
		try
		{
			final HttpRequest request = HttpRequest.newBuilder(URI.create(config("flamengo.ia.traducao.url",
					"https://api.anthropic.com/v1/messages")))
					.timeout(Duration.ofSeconds(timeout()))
					.header("content-type", "application/json")
					.header("x-api-key", config("flamengo.ia.traducao.chave", ""))
					.header("anthropic-version", "2023-06-01")
					.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
					.build();
			final HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200)
			{
				LOG.warn("Tradução com IA: resposta HTTP {} ({})", Integer.valueOf(response.statusCode()),
						StringUtils.abbreviate(response.body(), 300));
				throw new IllegalStateException(response.statusCode() == 401 || response.statusCode() == 403
						? "A chave da tradução com IA foi recusada. Confira a chave no servidor."
						: "O serviço de tradução respondeu com erro (" + response.statusCode() + "). Tente de novo.");
			}
			final JsonNode root = mapper.readTree(response.body());
			final StringBuilder out = new StringBuilder();
			for (final JsonNode part : root.path("content"))
			{
				if ("text".equals(part.path("type").asText()))
				{
					out.append(part.path("text").asText());
				}
			}
			final String traduzido = StringUtils.strip(out.toString().trim(), "\"“”");
			if (StringUtils.isBlank(traduzido))
			{
				throw new IllegalStateException("O serviço de tradução não devolveu texto. Tente de novo.");
			}
			return traduzido;
		}
		catch (final IOException e)
		{
			LOG.warn("Tradução com IA indisponível: {}", e.getMessage());
			throw new IllegalStateException("Não foi possível falar com o serviço de tradução. Confira a rede do servidor.", e);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("A tradução foi interrompida.", e);
		}
	}

	protected static String nome(final String idioma)
	{
		if ("pt".equalsIgnoreCase(idioma))
		{
			return "português do Brasil";
		}
		if ("en".equalsIgnoreCase(idioma))
		{
			return "inglês";
		}
		if ("es".equalsIgnoreCase(idioma))
		{
			return "espanhol";
		}
		return idioma;
	}

	protected long timeout()
	{
		final long s = configurationService.getConfiguration().getLong("flamengo.ia.traducao.timeoutSegundos", 20L);
		return s > 0 ? s : 20L;
	}

	protected String config(final String key, final String fallback)
	{
		return StringUtils.defaultIfBlank(configurationService.getConfiguration().getString(key), fallback);
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

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}
}
