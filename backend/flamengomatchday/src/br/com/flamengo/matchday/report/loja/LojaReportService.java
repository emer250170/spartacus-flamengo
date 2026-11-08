package br.com.flamengo.matchday.report.loja;

import br.com.flamengo.matchday.report.summary.SummaryReport;

import java.util.Date;


/** Relatório de vendas da perspectiva Loja Flamengo (Backoffice, gestor da loja). */
public interface LojaReportService
{
	/** Tipo extra deste relatório: camisetas do Mengo Estúdio (as outras chaves são as do SummaryReportService) */
	String ESTUDIO = "ESTUDIO";
	String DISCOUNTS = "DISCOUNTS";

	/** Formas de pagamento */
	String PAY_CARD = "CARD";
	String PAY_PIX = "PIX";
	String PAY_BOLETO = "BOLETO";
	String PAY_OTHER = "OTHER";

	/** Como o torcedor recebe */
	String DELIVERY_HOME = "HOME";
	String DELIVERY_PICKUP = "PICKUP";
	String DELIVERY_DIGITAL = "DIGITAL";

	/** Relatório do período, gerado em {@code now} */
	LojaReport getReport(SummaryReport.Period period, Date now);
}
