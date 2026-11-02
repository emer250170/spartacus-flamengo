package br.com.flamengo.matchday.socio;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Pontos do sócio (item 8): saldo, de onde vieram, resgates e a vitrine de
 * experiências com o que o torcedor pode resgatar. Montado pelo
 * {@link SocioPointsService}; lido pela API de comércio (sociopoints), pela ficha do
 * Backoffice e pelo Claude.
 */
public class SocioPoints
{
	/** Chave flamengo.socio.pontos.ativo */
	public boolean enabled;
	/** Já foi sócio (tem adesão); sem isso não há pontos */
	public boolean member;
	/** Pode resgatar agora (sócio em dia) */
	public boolean canRedeem;
	/** Por que não pode resgatar (null quando pode) */
	public String reason;
	public String planCode;
	public String planName;
	/** Desde quando os pedidos contam (adesão) */
	public Date since;
	public int perTicket;
	public int perReal;
	/** Bônus de adesão (entra uma vez, na data da adesão) */
	public int bonus;
	/** Pontos ganhos nos pedidos */
	public int earned;
	/** Pontos usados em resgates não cancelados */
	public int redeemed;
	public int balance;
	public int tickets;
	/** Total em produtos que contou pontos (R$) */
	public double productsAmount;
	/** Pedidos que deram pontos, mais novo primeiro */
	public final List<Earning> earnings = new ArrayList<>();
	/** Resgates, mais novo primeiro (cancelados também) */
	public final List<Redemption> redemptions = new ArrayList<>();
	/** Vitrine (experiências ativas, na ordem cadastrada) */
	public final List<Experience> experiences = new ArrayList<>();

	public static class Earning
	{
		/** null = bônus de adesão */
		public String orderCode;
		public Date date;
		public int tickets;
		public double productsAmount;
		public int points;
	}

	public static class Redemption
	{
		public String voucher;
		public String experienceCode;
		public String experienceName;
		public int points;
		public Date date;
		public String origin;
		public Date cancelledAt;
	}

	public static class Experience
	{
		public String code;
		public String name;
		public String description;
		public int points;
		/** Vagas que sobram (null = sem limite) */
		public Integer available;
		public String minPlanCode;
		public String minPlanName;
		/** Este torcedor pode resgatar esta agora */
		public boolean redeemable;
		/** Por que não (saldo, plano, esgotada, situação) */
		public String reason;
	}
}
