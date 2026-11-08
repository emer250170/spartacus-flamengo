package br.com.flamengo.matchday.mcp;

import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Relatório pedido por um gestor pelo Claude (MCP), tipo FlaReportMail: ao ser criado, confere os destinatários
 * contra a lista liberada no servidor e envia o e-mail pelo Commerce depois da gravação (situação ENVIANDO,
 * depois ENVIADO ou ERRO).
 * <p>
 * Chaves: {@code flamengo.mcp.relatorio.ativo} (false = recusa pedidos novos) e
 * {@code flamengo.mcp.relatorio.destinatarios} (e-mails liberados, separados por vírgula; vazio = nenhum).
 * Destinatário fora da lista: o pedido é recusado e nada é gravado nem enviado.
 */
public class FlaReportMailInterceptor implements PrepareInterceptor<ItemModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaReportMailInterceptor.class);

	static final String TYPE = "FlaReportMail";
	static final String CHAVE_ATIVO = "flamengo.mcp.relatorio.ativo";
	static final String CHAVE_DESTINATARIOS = "flamengo.mcp.relatorio.destinatarios";
	private static final int MAX_BODY = 400_000;
	private static final int MAX_CSV = 2_000_000;
	private static final String[] AREA_NAMES = { "LOJA", "Gestor da loja", "JOGOS", "Gestor de jogos", "SOCIO", "Gestor do sócio-torcedor" };

	private McpSupport mcpSupport;

	@Override
	public void onPrepare(final ItemModel item, final InterceptorContext ctx) throws InterceptorException
	{
		if (!ctx.isNew(item))
		{
			return;
		}
		final ModelService ms = ctx.getModelService();
		try
		{
			if (!mcpSupport.flag(CHAVE_ATIVO, true))
			{
				throw new IllegalArgumentException("O envio de relatórios pelo Claude está desligado no servidor (" + CHAVE_ATIVO + ").");
			}
			final UserModel user = mcpSupport.currentUser();
			final String area = mcpSupport.resolveArea(ms.getAttributeValue(item, "area"), user);

			final List<String> to = McpSupport.emails(ms.getAttributeValue(item, "recipients"));
			if (to.isEmpty())
			{
				throw new IllegalArgumentException("Informe ao menos um destinatário.");
			}
			final Set<String> allowed = new TreeSet<>(McpSupport.emails(mcpSupport.config(CHAVE_DESTINATARIOS, "")));
			for (final String email : to)
			{
				if (!allowed.contains(email))
				{
					throw new IllegalArgumentException("O destinatário " + email + " não está liberado para receber relatórios. Liberados: "
							+ (allowed.isEmpty() ? "nenhum" : String.join(", ", allowed)) + " (" + CHAVE_DESTINATARIOS + ").");
				}
			}
			final String subject = StringUtils.trimToNull(ms.getAttributeValue(item, "subject"));
			final String body = ms.getAttributeValue(item, "body");
			if (subject == null || StringUtils.isBlank(body))
			{
				throw new IllegalArgumentException("Informe o assunto e o conteúdo do relatório.");
			}
			if (body.length() > MAX_BODY || StringUtils.length(ms.getAttributeValue(item, "csv")) > MAX_CSV)
			{
				throw new IllegalArgumentException("Relatório grande demais para e-mail: resuma as tabelas.");
			}
			ms.setAttributeValue(item, "subject", StringUtils.abbreviate(subject, 200));
			ms.setAttributeValue(item, "recipients", String.join(", ", to));
			ms.setAttributeValue(item, "area", area);
			ms.setAttributeValue(item, "requestedBy", user.getUid());
			ms.setAttributeValue(item, "status", "ENVIANDO");
			ms.setAttributeValue(item, "message", "Enviando para " + String.join(", ", to));
		}
		catch (final IllegalArgumentException e)
		{
			throw new InterceptorException(e.getMessage());
		}
		final String code = ms.getAttributeValue(item, "code");
		mcpSupport.afterCommit(TYPE, code, this::send);
	}

	/** Monta o e-mail (moldura da loja + conteúdo + CSV) e envia; grava ENVIADO ou ERRO */
	protected void send(final ItemModel item)
	{
		final de.hybris.platform.servicelayer.model.ModelService ms = mcpSupport.getModelService();
		final String code = ms.getAttributeValue(item, "code");
		try
		{
			final List<String> to = McpSupport.emails(ms.getAttributeValue(item, "recipients"));
			final String subject = ms.getAttributeValue(item, "subject");
			final String area = ms.getAttributeValue(item, "area");
			final String footer = "Relatório montado pelo Claude (MCP) a pedido de " + McpSupport.esc(ms.getAttributeValue(item, "requestedBy"))
					+ " (" + areaName(area) + ") em " + McpSupport.when(new Date()) + ", com dados do SAP Commerce Cloud. Pedido " + McpSupport.esc(code) + ".";
			final String csv = ms.getAttributeValue(item, "csv");
			String csvName = StringUtils.defaultIfBlank(ms.getAttributeValue(item, "csvName"), "relatorio.csv")
					.replaceAll("[^A-Za-z0-9._-]+", "-");
			if (!csvName.toLowerCase(Locale.ROOT).endsWith(".csv"))
			{
				csvName = csvName + ".csv";
			}
			final String html = mcpSupport.frame(subject, McpSupport.safeHtml(ms.getAttributeValue(item, "body")), footer);
			final boolean ok = mcpSupport.send(to, subject + " · Loja Flamengo", html, csv, csvName);
			mcpSupport.status(item, ok ? "ENVIADO" : "ERRO",
					ok ? "Enviado para " + String.join(", ", to) + (StringUtils.isNotBlank(csv) ? " com o anexo " + csvName : "")
							: "O servidor de e-mail recusou o envio (veja o log do Commerce).",
					ok);
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha ao enviar o relatório " + code, e);
			mcpSupport.status(item, "ERRO", "Falha no envio: " + e.getMessage(), false);
		}
	}

	static String areaName(final String area)
	{
		for (int i = 0; i < AREA_NAMES.length; i += 2)
		{
			if (AREA_NAMES[i].equals(area))
			{
				return AREA_NAMES[i + 1];
			}
		}
		return StringUtils.defaultString(area);
	}

	public void setMcpSupport(final McpSupport mcpSupport)
	{
		this.mcpSupport = mcpSupport;
	}
}
