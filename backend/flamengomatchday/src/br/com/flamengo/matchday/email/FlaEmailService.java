package br.com.flamengo.matchday.email;

import de.hybris.platform.acceleratorservices.email.impl.DefaultEmailService;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.ImageHtmlEmail;
import org.apache.commons.mail.resolver.DataSourceUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Envio de e-mail com as imagens embutidas na mensagem (anexos em linha, "cid:").
 * <p>
 * O Outlook bloqueia imagens de link de remetente que ele não conhece ("clique aqui para baixar as imagens");
 * imagem embutida aparece direto. O {@link DefaultEmailService} monta a mensagem em
 * {@link #getPerConfiguredEmail()}; aqui ela vira um {@link ImageHtmlEmail}, que no envio baixa cada
 * {@code <img src="https://...">} do HTML e troca pelo anexo. O resto do envio (destinatários, assunto,
 * corpo, anexos, gravação do EmailMessage) segue o padrão.
 * <p>
 * Chave: {@code flamengo.email.imagensEmbutidas} (false = envio padrão, com as imagens por link). Imagem que
 * não baixar fica como link (resolvedor tolerante), sem impedir o envio.
 */
public class FlaEmailService extends DefaultEmailService
{
	private static final Logger LOG = LoggerFactory.getLogger(FlaEmailService.class);

	static final String CHAVE_ATIVO = "flamengo.email.imagensEmbutidas";
	static final String CHAVE_URL_BASE = "flamengo.email.imagens.urlBase";
	private static final String URL_BASE_PADRAO = "https://spartacus-flamengo.democc.online/";

	@Override
	protected HtmlEmail getPerConfiguredEmail() throws EmailException
	{
		final HtmlEmail padrao = super.getPerConfiguredEmail();
		if (!getConfigurationService().getConfiguration().getBoolean(CHAVE_ATIVO, false))
		{
			return padrao;
		}
		try
		{
			final ImageHtmlEmail email = new ImageHtmlEmail();
			// a sessão já traz servidor, porta, TLS e o autenticador configurados pelo Commerce (mail.*)
			email.setMailSession(padrao.getMailSession());
			if (padrao.getFromAddress() != null)
			{
				email.setFrom(padrao.getFromAddress().getAddress(), padrao.getFromAddress().getPersonal());
			}
			if (padrao.getReplyToAddresses() != null && !padrao.getReplyToAddresses().isEmpty())
			{
				email.setReplyTo(padrao.getReplyToAddresses());
			}
			if (padrao.getBounceAddress() != null)
			{
				email.setBounceAddress(padrao.getBounceAddress());
			}
			final String urlBase = getConfigurationService().getConfiguration().getString(CHAVE_URL_BASE, URL_BASE_PADRAO);
			email.setDataSourceResolver(new DataSourceUrlResolver(new URL(urlBase), true));
			return email;
		}
		catch (final MalformedURLException | RuntimeException e)
		{
			LOG.warn("Imagens embutidas indisponíveis, enviando com imagens por link: {}", e.getMessage());
			return padrao;
		}
	}
}
