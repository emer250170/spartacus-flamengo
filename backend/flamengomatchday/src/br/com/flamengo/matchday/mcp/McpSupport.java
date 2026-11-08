package br.com.flamengo.matchday.mcp;

import de.hybris.platform.acceleratorservices.email.EmailService;
import de.hybris.platform.catalog.CatalogVersionService;
import de.hybris.platform.acceleratorservices.model.email.EmailAddressModel;
import de.hybris.platform.acceleratorservices.model.email.EmailAttachmentModel;
import de.hybris.platform.acceleratorservices.model.email.EmailMessageModel;
import de.hybris.platform.core.Registry;
import de.hybris.platform.core.Tenant;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.search.restriction.SearchRestrictionService;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Apoio das ações que os gestores pedem pelo Claude (MCP): área de quem pediu, envio de e-mail pelo Commerce
 * (o mesmo serviço dos e-mails da loja, SMTP de mail.*) e execução depois da gravação.
 * <p>
 * Os tipos FlaReportMail e FlaCouponGrant são lidos e gravados pelo nome dos atributos
 * ({@link ModelService#getAttributeValue}), sem depender das classes geradas.
 */
public class McpSupport
{
	private static final Logger LOG = LoggerFactory.getLogger(McpSupport.class);

	public static final String AREA_LOJA = "LOJA";
	public static final String AREA_JOGOS = "JOGOS";
	public static final String AREA_SOCIO = "SOCIO";

	static final String CHAVE_LOJA_URL = "flamengo.mcp.loja.url";
	private static final String LOJA_URL_PADRAO = "https://spartacus-flamengo.democc.online/electronics-spa/pt/BRL";
	private static final TimeZone BRASILIA = TimeZone.getTimeZone("America/Sao_Paulo");

	private ModelService modelService;
	private FlexibleSearchService flexibleSearchService;
	private UserService userService;
	private SessionService sessionService;
	private ConfigurationService configurationService;
	private EmailService emailService;
	private SearchRestrictionService searchRestrictionService;
	private CatalogVersionService catalogVersionService;

	// ------------------------------------------------------------ quem pediu

	/** Usuário da sessão (na Integration API, o gestor que fez a chamada) */
	public UserModel currentUser()
	{
		return userService.getCurrentUser();
	}

	/**
	 * Área do usuário pelos grupos de API: lojaapigroup, jogosapigroup, socioapigroup. Administrador vale para todas
	 * (devolve null). Sem nenhum desses grupos: vazio.
	 */
	public Set<String> areasOf(final UserModel user)
	{
		final Set<String> areas = new LinkedHashSet<>();
		if (user == null)
		{
			return areas;
		}
		if (userService.isAdmin(user))
		{
			areas.add(AREA_LOJA);
			areas.add(AREA_JOGOS);
			areas.add(AREA_SOCIO);
			return areas;
		}
		if (member(user, "lojaapigroup"))
		{
			areas.add(AREA_LOJA);
		}
		if (member(user, "jogosapigroup"))
		{
			areas.add(AREA_JOGOS);
		}
		if (member(user, "socioapigroup"))
		{
			areas.add(AREA_SOCIO);
		}
		return areas;
	}

	protected boolean member(final UserModel user, final String groupUid)
	{
		try
		{
			final UserGroupModel group = userService.getUserGroupForUID(groupUid);
			return userService.isMemberOfGroup(user, group);
		}
		catch (final RuntimeException e)
		{
			return false;
		}
	}

	/** Área pedida, conferida com os grupos de quem pediu; sem área pedida, a única que ele tem */
	public String resolveArea(final String requested, final UserModel user)
	{
		final Set<String> areas = areasOf(user);
		if (areas.isEmpty())
		{
			throw new IllegalArgumentException("O usuário " + (user == null ? "?" : user.getUid())
					+ " não é gestor de nenhuma área (loja, jogos ou sócio-torcedor).");
		}
		final String wanted = StringUtils.upperCase(StringUtils.trimToNull(requested), Locale.ROOT);
		if (wanted == null)
		{
			return areas.iterator().next();
		}
		if (!areas.contains(wanted))
		{
			throw new IllegalArgumentException("O usuário " + user.getUid() + " não é gestor da área " + wanted + ".");
		}
		return wanted;
	}

	// ------------------------------------------------------------ configuração

	public boolean flag(final String key, final boolean fallback)
	{
		return configurationService.getConfiguration().getBoolean(key, fallback);
	}

	public String config(final String key, final String fallback)
	{
		return StringUtils.defaultIfBlank(configurationService.getConfiguration().getString(key, fallback), fallback);
	}

	public double number(final String key, final double fallback)
	{
		try
		{
			return Double.parseDouble(config(key, String.valueOf(fallback)).trim().replace(',', '.'));
		}
		catch (final NumberFormatException e)
		{
			return fallback;
		}
	}

	public String storeUrl()
	{
		return StringUtils.removeEnd(config(CHAVE_LOJA_URL, LOJA_URL_PADRAO), "/");
	}

	// ------------------------------------------------------------ e-mail

	/** "a@x, b@y; c@z" -> [a@x, b@y, c@z] (minúsculas, sem repetidos) */
	public static List<String> emails(final String text)
	{
		final Set<String> out = new LinkedHashSet<>();
		for (final String part : StringUtils.defaultString(text).split("[,;\\s]+"))
		{
			final String email = part.trim().toLowerCase(Locale.ROOT);
			if (!email.isEmpty())
			{
				out.add(email);
			}
		}
		return new ArrayList<>(out);
	}

	public static boolean validEmail(final String email)
	{
		return email != null && email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
	}

	/**
	 * Envia pelo serviço de e-mail do Commerce (FlaEmailService). Remetente: mail.from, como os e-mails da loja.
	 *
	 * @param csv
	 *           conteúdo do anexo CSV (null = sem anexo)
	 * @return true se o servidor de e-mail aceitou
	 */
	public boolean send(final List<String> to, final String subject, final String html, final String csv, final String csvName)
	{
		final List<EmailAddressModel> toAddresses = new ArrayList<>();
		for (final String email : to)
		{
			toAddresses.add(emailService.getOrCreateEmailAddressForEmail(email, email));
		}
		final String fromEmail = config("mail.from", "noreply@flamengo.com.br");
		final EmailAddressModel from = emailService.getOrCreateEmailAddressForEmail(fromEmail, "Loja Flamengo");
		final List<EmailAttachmentModel> attachments = new ArrayList<>();
		if (StringUtils.isNotBlank(csv))
		{
			// BOM: o Excel abre o CSV em UTF-8 com os acentos certos
			final byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
			attachments.add(emailService.createEmailAttachment(new DataInputStream(new ByteArrayInputStream(bytes)),
					StringUtils.defaultIfBlank(csvName, "relatorio.csv"), "text/csv"));
		}
		final EmailMessageModel message = emailService.createEmailMessage(toAddresses, Collections.emptyList(),
				Collections.emptyList(), from, fromEmail, subject, html, attachments);
		return emailService.send(message);
	}

	/** Moldura dos e-mails do MCP: faixa vermelha da Loja Flamengo, conteúdo e rodapé */
	public String frame(final String title, final String content, final String footer)
	{
		return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width\">"
				+ "<title>" + esc(title) + "</title></head>"
				+ "<body style=\"margin:0;padding:0;background:#f4f4f4;font-family:Arial,Helvetica,sans-serif;color:#1a1a1a\">"
				+ "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f4f4\"><tr><td align=\"center\" style=\"padding:16px\">"
				+ "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:720px;background:#ffffff;border-radius:6px;overflow:hidden\">"
				+ "<tr><td style=\"background:#c4161c;color:#ffffff;padding:14px 20px;font-size:14px;font-weight:bold;letter-spacing:.5px\">LOJA FLAMENGO</td></tr>"
				+ "<tr><td style=\"padding:20px 20px 4px 20px\"><h1 style=\"margin:0;font-size:20px;color:#1a1a1a\">" + esc(title) + "</h1></td></tr>"
				+ "<tr><td style=\"padding:8px 20px 20px 20px;font-size:14px;line-height:1.5\">" + content + "</td></tr>"
				+ "<tr><td style=\"background:#1a1a1a;color:#bdbdbd;padding:12px 20px;font-size:11px;line-height:1.4\">" + footer + "</td></tr>"
				+ "</table></td></tr></table></body></html>";
	}

	/** Tira do HTML o que não pode ir num e-mail (script, iframe, formulário, eventos) */
	public static String safeHtml(final String html)
	{
		return StringUtils.defaultString(html)
				.replaceAll("(?is)<(script|style|iframe|object|embed|form)[^>]*>.*?</\\1\\s*>", "")
				.replaceAll("(?is)<(script|iframe|object|embed|form|input|button|link|meta)[^>]*/?>", "")
				.replaceAll("(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "")
				.replaceAll("(?i)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2", "$1=$2#$2");
	}

	public static String esc(final String text)
	{
		return StringUtils.defaultString(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	public static String when(final Date date)
	{
		final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
		format.setTimeZone(BRASILIA);
		return format.format(date);
	}

	public static String day(final Date date)
	{
		final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));
		format.setTimeZone(BRASILIA);
		return format.format(date);
	}

	// ------------------------------------------------------------ depois da gravação

	/** Item do tipo pelo código (null se ainda não estiver gravado) */
	public ItemModel find(final String type, final String code)
	{
		final FlexibleSearchQuery query = new FlexibleSearchQuery("SELECT {pk} FROM {" + type + "} WHERE {code}=?code");
		query.addQueryParameter("code", code);
		final List<ItemModel> found = flexibleSearchService.<ItemModel> search(query).getResult();
		return found.isEmpty() ? null : found.get(0);
	}

	/**
	 * Roda a ação numa linha de execução própria, como administrador, depois que o item estiver gravado (o
	 * interceptor roda antes do commit; espera até 60 s o item aparecer).
	 */
	public void afterCommit(final String type, final String code, final Consumer<ItemModel> action)
	{
		final Tenant tenant = Registry.getCurrentTenant();
		final Thread thread = new Thread(() -> {
			Registry.setCurrentTenant(tenant);
			try
			{
				sessionService.executeInLocalView(new SessionExecutionBody()
				{
					@Override
					public void executeWithoutResult()
					{
						prepareSession();
						ItemModel item = null;
						for (int i = 0; i < 60 && item == null; i++)
						{
							sleep(1000);
							item = find(type, code);
						}
						if (item == null)
						{
							LOG.warn("{} {} não foi gravado; nada a fazer", type, code);
							return;
						}
						action.accept(item);
					}
				});
			}
			catch (final Exception e)
			{
				LOG.error("Falha no processamento de " + type + " " + code, e);
			}
			finally
			{
				Registry.unsetCurrentTenant();
			}
		}, "mcp-" + type + "-" + code);
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Sessão do processamento em segundo plano: administrador, sem restrições de busca e com todos os catálogos. A linha
	 * nasce da chamada da API sem catálogos na sessão, e a importação de ImpEx fecha a sessão no fim (o usuário volta a
	 * anônimo); sem isso as restrições de produto e categoria (Frontend_Product, Backend_visibility) falham em
	 * "session.catalogversions". Chamar no início e depois de cada importação.
	 */
	public void prepareSession()
	{
		userService.setCurrentUser(userService.getAdminUser());
		searchRestrictionService.disableSearchRestrictions();
		catalogVersionService.setSessionCatalogVersions(catalogVersionService.getAllCatalogVersions());
	}

	/** Grava situação e mensagem do item (ENVIADO, ERRO...) */
	public void status(final ItemModel item, final String status, final String message, final boolean sent)
	{
		modelService.refresh(item);
		modelService.setAttributeValue(item, "status", status);
		modelService.setAttributeValue(item, "message", StringUtils.abbreviate(message, 4000));
		if (sent)
		{
			modelService.setAttributeValue(item, "sentAt", new Date());
		}
		modelService.save(item);
	}

	static void sleep(final long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch (final InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	// ------------------------------------------------------------ injeção

	public ModelService getModelService()
	{
		return modelService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public FlexibleSearchService getFlexibleSearchService()
	{
		return flexibleSearchService;
	}

	public void setFlexibleSearchService(final FlexibleSearchService flexibleSearchService)
	{
		this.flexibleSearchService = flexibleSearchService;
	}

	public UserService getUserService()
	{
		return userService;
	}

	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setConfigurationService(final ConfigurationService configurationService)
	{
		this.configurationService = configurationService;
	}

	public void setEmailService(final EmailService emailService)
	{
		this.emailService = emailService;
	}

	public void setSearchRestrictionService(final SearchRestrictionService searchRestrictionService)
	{
		this.searchRestrictionService = searchRestrictionService;
	}

	public void setCatalogVersionService(final CatalogVersionService catalogVersionService)
	{
		this.catalogVersionService = catalogVersionService;
	}
}
