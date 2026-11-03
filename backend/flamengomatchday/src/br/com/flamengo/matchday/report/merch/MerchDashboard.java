package br.com.flamengo.matchday.report.merch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Números do painel inicial do gestor de merchandising (Cockpit de administração):
 * regras de promoção, cupons e desconto concedido nos pedidos.
 */
public class MerchDashboard
{
	public Date generatedAt;
	/** Grupo de promoção considerado (site da loja), ex.: electronicsPromoGrp */
	public String promotionGroup;
	/** Janela de uso de cupons e de desconto (dias) */
	public int days;
	/** Janela de "vencendo" (dias) */
	public int expiringDays;

	// ---------- regras (última versão de cada código) ----------
	public int rulesPublished;
	/** Rascunho: não publicada ou alterada depois de publicada */
	public int rulesDraft;
	/** Publicadas com fim de vigência dentro da janela de "vencendo" */
	public int rulesExpiring;
	/** Publicadas, ordenadas por prioridade (maior primeiro) */
	public final List<Rule> published = new ArrayList<>();

	// ---------- cupons ----------
	public int couponsActive;
	public int couponsTotal;
	/** Resgates de cupom na janela */
	public int redemptions;
	/** Cupons mais usados na janela (até 5) */
	public final List<CouponUse> topCoupons = new ArrayList<>();

	// ---------- desconto ----------
	public int orders;
	public int ordersWithDiscount;
	public int ordersWithCoupon;
	/** Desconto total (pedido + itens) na janela, em reais */
	public double discount;
	public String currency;

	public static class Rule
	{
		public String code;
		public String name;
		public Integer priority;
		public Date start;
		public Date end;
		public String ruleGroup;
		/** Vence dentro da janela de "vencendo" */
		public boolean expiring;
	}

	public static class CouponUse
	{
		public String couponId;
		public String name;
		public String kind;
		public boolean active;
		public int uses;
	}
}
