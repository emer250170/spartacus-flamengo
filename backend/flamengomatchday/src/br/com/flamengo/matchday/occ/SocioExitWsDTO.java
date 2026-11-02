package br.com.flamengo.matchday.occ;

import java.io.Serializable;


/**
 * Regras de saída do sócio na API de comércio: simulação do reembolso
 * (arrependimento) ou da multa e o valor do último cancelamento confirmado.
 */
public class SocioExitWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	/** Chave flamengo.socio.saida.ativo */
	private Boolean enabled;
	/** É sócio (há simulação) */
	private Boolean member;
	private String planCode;
	private String planName;
	/** Pedido da adesão ao plano atual */
	private String orderCode;
	/** Início do contrato (ISO com fuso) */
	private String start;
	/** Data que vale para o cálculo: pedido de cancelamento ou agora */
	private String basis;
	/** Prazo de arrependimento em dias corridos */
	private Integer withdrawalDays;
	/** Último dia do prazo */
	private String withdrawalUntil;
	private Boolean withinWithdrawal;
	private Integer installments;
	private Integer paidInstallments;
	private Integer remainingInstallments;
	/** Mensalidade com convidados */
	private Double monthly;
	private Integer guests;
	/** Pago na adesão (plano + convidados) */
	private Double paidAtJoin;
	private Double finePercent;
	private Double refund;
	private Double fine;
	/** Uma linha em português */
	private String summary;
	/** Valor de cancelamento já confirmado */
	private Boolean confirmed;
	/** Chamado de cancelamento em aberto */
	private String openTicket;
	/** A saída foi o fim da vigência sem renovação (sem multa) */
	private Boolean endOfTerm;
	/** Último cancelamento confirmado (ex-sócio) */
	private SocioExitWsDTO settlement;

	public Boolean getEnabled()
	{
		return enabled;
	}

	public void setEnabled(final Boolean enabled)
	{
		this.enabled = enabled;
	}

	public Boolean getMember()
	{
		return member;
	}

	public void setMember(final Boolean member)
	{
		this.member = member;
	}

	public String getPlanCode()
	{
		return planCode;
	}

	public void setPlanCode(final String planCode)
	{
		this.planCode = planCode;
	}

	public String getPlanName()
	{
		return planName;
	}

	public void setPlanName(final String planName)
	{
		this.planName = planName;
	}

	public String getOrderCode()
	{
		return orderCode;
	}

	public void setOrderCode(final String orderCode)
	{
		this.orderCode = orderCode;
	}

	public String getStart()
	{
		return start;
	}

	public void setStart(final String start)
	{
		this.start = start;
	}

	public String getBasis()
	{
		return basis;
	}

	public void setBasis(final String basis)
	{
		this.basis = basis;
	}

	public Integer getWithdrawalDays()
	{
		return withdrawalDays;
	}

	public void setWithdrawalDays(final Integer withdrawalDays)
	{
		this.withdrawalDays = withdrawalDays;
	}

	public String getWithdrawalUntil()
	{
		return withdrawalUntil;
	}

	public void setWithdrawalUntil(final String withdrawalUntil)
	{
		this.withdrawalUntil = withdrawalUntil;
	}

	public Boolean getWithinWithdrawal()
	{
		return withinWithdrawal;
	}

	public void setWithinWithdrawal(final Boolean withinWithdrawal)
	{
		this.withinWithdrawal = withinWithdrawal;
	}

	public Integer getInstallments()
	{
		return installments;
	}

	public void setInstallments(final Integer installments)
	{
		this.installments = installments;
	}

	public Integer getPaidInstallments()
	{
		return paidInstallments;
	}

	public void setPaidInstallments(final Integer paidInstallments)
	{
		this.paidInstallments = paidInstallments;
	}

	public Integer getRemainingInstallments()
	{
		return remainingInstallments;
	}

	public void setRemainingInstallments(final Integer remainingInstallments)
	{
		this.remainingInstallments = remainingInstallments;
	}

	public Double getMonthly()
	{
		return monthly;
	}

	public void setMonthly(final Double monthly)
	{
		this.monthly = monthly;
	}

	public Integer getGuests()
	{
		return guests;
	}

	public void setGuests(final Integer guests)
	{
		this.guests = guests;
	}

	public Double getPaidAtJoin()
	{
		return paidAtJoin;
	}

	public void setPaidAtJoin(final Double paidAtJoin)
	{
		this.paidAtJoin = paidAtJoin;
	}

	public Double getFinePercent()
	{
		return finePercent;
	}

	public void setFinePercent(final Double finePercent)
	{
		this.finePercent = finePercent;
	}

	public Double getRefund()
	{
		return refund;
	}

	public void setRefund(final Double refund)
	{
		this.refund = refund;
	}

	public Double getFine()
	{
		return fine;
	}

	public void setFine(final Double fine)
	{
		this.fine = fine;
	}

	public String getSummary()
	{
		return summary;
	}

	public void setSummary(final String summary)
	{
		this.summary = summary;
	}

	public Boolean getConfirmed()
	{
		return confirmed;
	}

	public void setConfirmed(final Boolean confirmed)
	{
		this.confirmed = confirmed;
	}

	public String getOpenTicket()
	{
		return openTicket;
	}

	public void setOpenTicket(final String openTicket)
	{
		this.openTicket = openTicket;
	}

	public Boolean getEndOfTerm()
	{
		return endOfTerm;
	}

	public void setEndOfTerm(final Boolean endOfTerm)
	{
		this.endOfTerm = endOfTerm;
	}

	public SocioExitWsDTO getSettlement()
	{
		return settlement;
	}

	public void setSettlement(final SocioExitWsDTO settlement)
	{
		this.settlement = settlement;
	}
}
