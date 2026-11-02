package br.com.flamengo.matchday.socio;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Vigência do sócio (item 6 do plano de evolução): plano, início e fim, renovação
 * automática e o plano da próxima vigência. Sem contrato gravado, os valores vêm
 * do pedido mais novo do plano (como antes): {@link #persisted} = false.
 */
public class SocioContractStatus
{
	/** Chave flamengo.socio.vigencia.ativo */
	public boolean enabled;
	/** É sócio (tem plano) */
	public boolean member;
	public String planCode;
	public String planName;
	public Date start;
	public Date end;
	public boolean autoRenew = true;
	/** Mudança para um plano menor agendada para a próxima vigência */
	public String nextPlanCode;
	public String nextPlanName;
	/** Último dia para desligar a renovação ou agendar a mudança (fim - prazo) */
	public Date changeDeadline;
	/** Prazo passou: renovação e mudança travadas até a virada */
	public boolean locked;
	/** Vigência atual veio de uma renovação (não de um pedido) */
	public boolean renewed;
	public int renewals;
	/** "Pedido 00017000" ou "Renovação" */
	public String origin;
	/** Há contrato gravado (SocioContract) */
	public boolean persisted;
	/** Encerrado sem renovação (ex-sócio) */
	public Date endedAt;
	/** Planos menores para agendar a mudança */
	public final List<Option> options = new ArrayList<>();

	public static class Option
	{
		public String code;
		public String name;
		public double monthlyPrice;
	}
}
