package br.com.flamengo.matchday.backoffice.widgets.experiences;

import br.com.flamengo.matchday.backoffice.widgets.report.MatchdayReportHtml;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;

import com.hybris.cockpitng.i18n.CockpitLocaleService;
import com.hybris.cockpitng.util.DefaultWidgetController;


/**
 * Apoio dos widgets do cockpit Gestão de experiências (item 9): rótulos, formatos e avisos.
 */
public abstract class ExperienceWidgetSupport extends DefaultWidgetController
{
	private static final long serialVersionUID = 1L;
	protected static final TimeZone TZ = TimeZone.getTimeZone("America/Sao_Paulo");
	protected static final Locale PT = new Locale("pt");

	@WireVariable
	protected transient CockpitLocaleService cockpitLocaleService;

	protected Locale locale()
	{
		final Locale locale = cockpitLocaleService == null ? null : cockpitLocaleService.getCurrentLocale();
		return locale == null ? Locale.ENGLISH : locale;
	}

	protected boolean portuguese()
	{
		return "pt".equals(locale().getLanguage());
	}

	protected String label(final String key)
	{
		final String value = getLabel(key);
		return StringUtils.isBlank(value) ? key : value;
	}

	protected String label(final String key, final Object... args)
	{
		return MessageFormat.format(label(key), args);
	}

	protected String money(final Double value)
	{
		if (value == null)
		{
			return "-";
		}
		final NumberFormat f = NumberFormat.getNumberInstance(portuguese() ? new Locale("pt", "BR") : Locale.US);
		f.setMinimumFractionDigits(2);
		f.setMaximumFractionDigits(2);
		return "R$" + (portuguese() ? " " : "") + f.format(value.doubleValue());
	}

	protected String number(final long value)
	{
		return NumberFormat.getIntegerInstance(portuguese() ? new Locale("pt", "BR") : Locale.US).format(value);
	}

	protected String date(final Date date, final boolean withTime)
	{
		if (date == null)
		{
			return "-";
		}
		final SimpleDateFormat f = new SimpleDateFormat(
				withTime ? (portuguese() ? "dd/MM/yyyy HH:mm" : "MMM d, yyyy h:mm a") : (portuguese() ? "dd/MM/yyyy" : "MMM d, yyyy"),
				portuguese() ? new Locale("pt", "BR") : Locale.US);
		f.setTimeZone(TZ);
		return f.format(date);
	}

	protected void info(final String message)
	{
		Clients.showNotification(StringUtils.defaultIfBlank(message, "OK"), "info", null, "top_center", 5000);
	}

	protected void error(final Throwable e)
	{
		Clients.showNotification(label("error", reason(e)), "error", null, "top_center", 8000);
	}

	protected void warn(final String message)
	{
		Clients.showNotification(message, "warning", null, "top_center", 5000);
	}

	/** Mensagem mais útil da exceção (a do interceptor ou do serviço) */
	protected static String reason(final Throwable error)
	{
		Throwable t = error;
		String message = error.getMessage();
		while (t != null)
		{
			final String type = t.getClass().getSimpleName();
			if (type.equals("InterceptorException") || type.equals("SocioPointsException") || t instanceof IllegalStateException)
			{
				message = t.getMessage();
			}
			t = t.getCause() == t ? null : t.getCause();
		}
		return StringUtils.defaultString(message).replaceFirst("^\\[[^\\]]*\\]:?\\s*", "");
	}

	protected static String esc(final String text)
	{
		return MatchdayReportHtml.esc(text);
	}
}
