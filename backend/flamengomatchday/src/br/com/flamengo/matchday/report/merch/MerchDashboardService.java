package br.com.flamengo.matchday.report.merch;

import java.util.Date;


/**
 * Painel inicial do gestor de merchandising (Backoffice › Cockpit de administração,
 * papel merchandisingmanagerrole).
 * <p>
 * Propriedades: flamengo.merch.painel.grupoPromocao (padrão electronicsPromoGrp),
 * flamengo.merch.painel.dias (padrão 30) e flamengo.merch.painel.vencendoDias (padrão 7).
 */
public interface MerchDashboardService
{
	String GROUP_PROPERTY = "flamengo.merch.painel.grupoPromocao";
	String DAYS_PROPERTY = "flamengo.merch.painel.dias";
	String EXPIRING_PROPERTY = "flamengo.merch.painel.vencendoDias";

	/** Números até {@code now} (null = agora) */
	MerchDashboard getDashboard(Date now);
}
