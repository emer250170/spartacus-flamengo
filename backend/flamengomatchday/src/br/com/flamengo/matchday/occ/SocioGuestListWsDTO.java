package br.com.flamengo.matchday.occ;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/** Resposta de Meus convidados: plano, vagas contratadas, convidados ativos e encerrados. */
public class SocioGuestListWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private Boolean enabled;
	private String planCode;
	private String planName;
	private Integer planMaxGuests;
	private Integer contracted;
	private Integer used;
	private Double guestPrice;
	private Boolean suspended;
	private Boolean canAdd;
	private String reason;
	private List<SocioGuestWsDTO> guests = new ArrayList<>();
	private List<SocioGuestWsDTO> ended = new ArrayList<>();

	public Boolean getEnabled()
	{
		return enabled;
	}

	public void setEnabled(final Boolean enabled)
	{
		this.enabled = enabled;
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

	public Integer getPlanMaxGuests()
	{
		return planMaxGuests;
	}

	public void setPlanMaxGuests(final Integer planMaxGuests)
	{
		this.planMaxGuests = planMaxGuests;
	}

	public Integer getContracted()
	{
		return contracted;
	}

	public void setContracted(final Integer contracted)
	{
		this.contracted = contracted;
	}

	public Integer getUsed()
	{
		return used;
	}

	public void setUsed(final Integer used)
	{
		this.used = used;
	}

	public Double getGuestPrice()
	{
		return guestPrice;
	}

	public void setGuestPrice(final Double guestPrice)
	{
		this.guestPrice = guestPrice;
	}

	public Boolean getSuspended()
	{
		return suspended;
	}

	public void setSuspended(final Boolean suspended)
	{
		this.suspended = suspended;
	}

	public Boolean getCanAdd()
	{
		return canAdd;
	}

	public void setCanAdd(final Boolean canAdd)
	{
		this.canAdd = canAdd;
	}

	public String getReason()
	{
		return reason;
	}

	public void setReason(final String reason)
	{
		this.reason = reason;
	}

	public List<SocioGuestWsDTO> getGuests()
	{
		return guests;
	}

	public void setGuests(final List<SocioGuestWsDTO> guests)
	{
		this.guests = guests;
	}

	public List<SocioGuestWsDTO> getEnded()
	{
		return ended;
	}

	public void setEnded(final List<SocioGuestWsDTO> ended)
	{
		this.ended = ended;
	}
}
