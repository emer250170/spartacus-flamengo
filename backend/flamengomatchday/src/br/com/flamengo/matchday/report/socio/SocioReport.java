package br.com.flamengo.matchday.report.socio;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Painel do programa sócio-torcedor (perspectiva Sócio-Torcedor › Relatórios),
 * no modelo do "Dashboard - Base" do clube. Números de hoje e do mês corrente,
 * no horário de Brasília. Base simulada dos planos (demonstração) + dados reais
 * da loja (sócios, pedidos com plano e carrinhos abandonados).
 */
public class SocioReport
{
	// ------------------------------------------------------------ cartões
	/** Titulares + convidados ativos */
	public int activeTotal;
	/** Titulares (contratos) ativos */
	public int contracts;
	/** Variação de hoje sobre ontem (fração: -0.0006 = -0,06%) */
	public double activeTotalDelta;
	public double contractsDelta;
	/** Um por programa (Nação Maraca, Nação Sem Fronteiras...), na ordem dos planos */
	public final List<Family> families = new ArrayList<>();

	// ------------------------------------------------------------ novos contratos de hoje
	public int renewals;
	public int joins;
	public int upgrades;
	/** Por programa: novos contratos por hora de hoje (0 a 23; horas futuras = 0) */
	public final List<Hourly> hourly = new ArrayList<>();
	/** Hora atual (0 a 23): o gráfico vai até ela */
	public int currentHour;

	// ------------------------------------------------------------ receita do mês
	/** Competências pagas no mês até agora (R$) */
	public double revenuePaid;
	/** Recorrências que ainda vão cair no mês (R$) */
	public double revenueFuture;
	/** Receita prevista do mês (R$) */
	public double revenueForecast;
	/** Receita paga por dia do mês (índice 0 = dia 1); dias depois de hoje = null */
	public final List<Double> revenueByDay = new ArrayList<>();
	/** Hoje (1 a 31) */
	public int dayOfMonth;
	public int daysInMonth;

	// ------------------------------------------------------------ base
	/** Por onda de compra: "1", "2", "3" e "0" (sem prioridade) */
	public final List<Base> byWave = new ArrayList<>();
	/** Por plano (nome do plano) */
	public final List<Base> byPlan = new ArrayList<>();

	// ------------------------------------------------------------ tabelas
	public final List<Coupon> coupons = new ArrayList<>();
	public final List<Churn> churn = new ArrayList<>();
	public final List<Join> joinsByPlan = new ArrayList<>();
	/** Top 10 estados das adesões Nação Sem Fronteiras no mês */
	public final List<State> states = new ArrayList<>();

	// ------------------------------------------------------------ cancelamentos pedidos na loja (reais)
	/**
	 * Chamados de atendimento "Cancelamento do plano ..." abertos pelos sócios em
	 * Minha conta › Meu sócio-torcedor.
	 */
	public int cancelOpen;
	public int cancelMonth;
	public int cancelTotal;
	/** Motivo (price, games, moving, service, other ou o texto livre) → pedidos, do mais citado ao menos */
	public final Map<String, Integer> cancelReasons = new LinkedHashMap<>();
	/** Últimos pedidos (mais novos primeiro) */
	public final List<Cancellation> cancellations = new ArrayList<>();

	// ------------------------------------------------------------ estornos e multas (item 5, cancelamentos confirmados)
	/** As regras de saída estão ligadas (flamengo.socio.saida.ativo) */
	public boolean exitEnabled;
	public int refundCount;
	public double refundTotal;
	public double refundMonth;
	public int fineCount;
	public double fineTotal;
	public double fineMonth;

	// ------------------------------------------------------------ origem dos números
	public int realMembers;
	public int realJoinsMonth;
	public int realAbandoned;
	public Date generatedAt;

	public static class Family
	{
		public String name;
		public int contracts;
		/** Variação de hoje sobre ontem (fração) */
		public double delta;
		public int inactive;
		public int upToDate;
		public int overdue;
		public int notRenewed;
		/** Sócios reais da loja com os benefícios suspensos (já contados em overdue) */
		public int realSuspended;
		/** Nomes desses sócios, para mostrar no cartão */
		public final List<String> suspendedNames = new ArrayList<>();
	}

	public static class Hourly
	{
		public String family;
		/** 24 posições */
		public int[] perHour = new int[24];
		/** Soma do dia até agora */
		public int total;
	}

	public static class Base
	{
		/** Nome do plano, ou a onda ("1", "2", "3", "0") em byWave */
		public String label;
		public int holders;
		public int guests;

		public int getTotal()
		{
			return holders + guests;
		}
	}

	public static class Coupon
	{
		public String code;
		/** Usos no mês */
		public int month;
		/** Usos hoje */
		public int today;
		/** Usos desde a criação */
		public int total;
	}

	public static class Churn
	{
		public String plan;
		/** Churn do mês (fração: 0.024 = 2,4%) */
		public double rate;
		/** Diferença para a média móvel de 12 meses, em pontos percentuais (1.8 = +1,8 p.p.) */
		public double vsAverage;
	}

	public static class Join
	{
		public String plan;
		/** Carrinhos com o plano, sem pedido, nos últimos 30 dias */
		public int abandoned;
		public int today;
		public int month;
		public int lastMonth;

		/** Mês atual sobre o mês passado (fração: -0.68 = -68%) */
		public double getVsLastMonth()
		{
			return lastMonth == 0 ? 0 : (month - lastMonth) / (double) lastMonth;
		}
	}

	public static class Cancellation
	{
		public String ticketId;
		public Date created;
		public String customerName;
		public String customerUid;
		public String plan;
		/** price, games, moving, service, other; null quando o texto não é um motivo conhecido */
		public String reasonKey;
		/** Texto do motivo como o sócio viu */
		public String reason;
		public String comment;
		/** Chamado ainda não fechado */
		public boolean open;
		/** Reembolso ou multa (R$): confirmado no evento de cancelamento ou previsto (pedido aberto) */
		public Double refund;
		public Double fine;
		/** Valor previsto (pedido ainda aberto), não confirmado */
		public boolean estimated;
	}

	public static class State
	{
		public String uf;
		public int count;
	}
}
