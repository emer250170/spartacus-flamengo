package br.com.flamengo.matchday.report.socio;

import java.util.Date;
import java.util.Locale;


/**
 * Painel do programa sócio-torcedor (Backoffice › Sócio-Torcedor › Relatórios).
 */
public interface SocioReportService
{
	/**
	 * @param now
	 *           momento do painel (hoje e mês corrente no horário de Brasília)
	 * @param locale
	 *           idioma dos nomes dos planos
	 */
	SocioReport getReport(Date now, Locale locale);
}
