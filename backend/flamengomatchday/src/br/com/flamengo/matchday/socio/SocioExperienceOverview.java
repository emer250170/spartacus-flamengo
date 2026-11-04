package br.com.flamengo.matchday.socio;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Números das experiências para o cockpit Gestão de experiências (vitrine com cards e
 * relatório): por experiência e por mês. Vouchers cancelados não contam como uso de vaga,
 * resgate nem venda.
 */
public class SocioExperienceOverview
{
	public Date generatedAt;
	public boolean saleEnabled;
	public final List<Line> lines = new ArrayList<>();
	/** Meses do mais antigo para o mais recente */
	public final List<Month> months = new ArrayList<>();
	public final Line total = new Line();

	public static class Line
	{
		public String code;
		public String name;
		public String description;
		public boolean active;
		public int points;
		/** Preço na loja (null = só resgate) */
		public Double price;
		/** Quantidade total (null = sem limite) */
		public Integer stock;
		/** Vagas que sobram (null = sem limite) */
		public Integer remaining;
		public String productCode;
		/** O produto está à venda na loja agora */
		public boolean onSale;
		public String minPlanName;
		public String statusMessage;
		/** Imagem da vitrine (/medias/...), ou null */
		public String imageUrl;
		/** Resgates com pontos válidos */
		public int redemptions;
		/** Compras válidas */
		public int purchases;
		/** Vouchers já utilizados */
		public int used;
		public int cancelled;
		public int pointsUsed;
		public double revenue;
	}

	public static class Month
	{
		/** yyyy-MM */
		public String key;
		public int redemptions;
		public int purchases;
		public int pointsUsed;
		public double revenue;
	}
}
