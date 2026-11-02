package br.com.flamengo.matchday.occ;

import java.io.Serializable;
import java.util.List;


/** Vigência do sócio na API de comércio: plano, início e fim, renovação e mudança agendada. */
public class SocioContractWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	/** Chave flamengo.socio.vigencia.ativo */
	private Boolean enabled;
	private Boolean member;
	private String planCode;
	private String planName;
	/** Início da vigência (ISO com fuso) */
	private String start;
	private String end;
	/** Renovação automática (também no POST /autorenew) */
	private Boolean autoRenew;
	/** Mudança agendada (também no POST /nextplan; vazio cancela) */
	private String nextPlanCode;
	private String nextPlanName;
	/** Último dia para mudar a renovação */
	private String changeDeadline;
	/** Prazo passou */
	private Boolean locked;
	/** Vigência veio de uma renovação */
	private Boolean renewed;
	private Integer renewals;
	private String origin;
	/** Encerrado sem renovação */
	private String endedAt;
	/** Planos menores para agendar */
	private List<SocioContractOptionWsDTO> options;
	/** Resultado da última mudança */
	private String message;

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

	public String getStart()
	{
		return start;
	}

	public void setStart(final String start)
	{
		this.start = start;
	}

	public String getEnd()
	{
		return end;
	}

	public void setEnd(final String end)
	{
		this.end = end;
	}

	public Boolean getAutoRenew()
	{
		return autoRenew;
	}

	public void setAutoRenew(final Boolean autoRenew)
	{
		this.autoRenew = autoRenew;
	}

	public String getNextPlanCode()
	{
		return nextPlanCode;
	}

	public void setNextPlanCode(final String nextPlanCode)
	{
		this.nextPlanCode = nextPlanCode;
	}

	public String getNextPlanName()
	{
		return nextPlanName;
	}

	public void setNextPlanName(final String nextPlanName)
	{
		this.nextPlanName = nextPlanName;
	}

	public String getChangeDeadline()
	{
		return changeDeadline;
	}

	public void setChangeDeadline(final String changeDeadline)
	{
		this.changeDeadline = changeDeadline;
	}

	public Boolean getLocked()
	{
		return locked;
	}

	public void setLocked(final Boolean locked)
	{
		this.locked = locked;
	}

	public Boolean getRenewed()
	{
		return renewed;
	}

	public void setRenewed(final Boolean renewed)
	{
		this.renewed = renewed;
	}

	public Integer getRenewals()
	{
		return renewals;
	}

	public void setRenewals(final Integer renewals)
	{
		this.renewals = renewals;
	}

	public String getOrigin()
	{
		return origin;
	}

	public void setOrigin(final String origin)
	{
		this.origin = origin;
	}

	public String getEndedAt()
	{
		return endedAt;
	}

	public void setEndedAt(final String endedAt)
	{
		this.endedAt = endedAt;
	}

	public List<SocioContractOptionWsDTO> getOptions()
	{
		return options;
	}

	public void setOptions(final List<SocioContractOptionWsDTO> options)
	{
		this.options = options;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(final String message)
	{
		this.message = message;
	}
}
