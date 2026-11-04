package br.com.flamengo.matchday.report.summary;

import java.util.Date;


/** Resumo de vendas do painel inicial do administrador (Backoffice > Cockpit de administração > Principal). */
public interface SummaryReportService
{
	/** Tipos de produto e serviço, na ordem da tela */
	String TICKETS = "TICKETS";
	String MEMBERSHIP = "MEMBERSHIP";
	String EXPERIENCES = "EXPERIENCES";
	String PARKING = "PARKING";
	String FOOD = "FOOD";
	String CUSTOMIZATION = "CUSTOMIZATION";
	String STORE = "STORE";
	String FREIGHT = "FREIGHT";

	/** Tipo de chamado próprio: o pedido de cancelamento do plano do sócio */
	String TICKET_PLAN_CANCEL = "PLAN_CANCEL";

	/** Resumo do período, gerado em {@code now} */
	SummaryReport getReport(SummaryReport.Period period, Date now);
}
