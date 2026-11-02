package br.com.flamengo.matchday.occ;

import java.io.Serializable;


/**
 * Convidado na API de comércio (Meus convidados). Na entrada (POST) vale name,
 * cpf, email, phone e birthDate (aaaa-mm-dd); na saída vêm também id, since,
 * until, endReason e active.
 */
public class SocioGuestWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String id;
	private String name;
	private String cpf;
	private String email;
	private String phone;
	private String birthDate;
	private Boolean active;
	private String since;
	private String until;
	private String endReason;

	public String getId()
	{
		return id;
	}

	public void setId(final String id)
	{
		this.id = id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public String getCpf()
	{
		return cpf;
	}

	public void setCpf(final String cpf)
	{
		this.cpf = cpf;
	}

	public String getEmail()
	{
		return email;
	}

	public void setEmail(final String email)
	{
		this.email = email;
	}

	public String getPhone()
	{
		return phone;
	}

	public void setPhone(final String phone)
	{
		this.phone = phone;
	}

	public String getBirthDate()
	{
		return birthDate;
	}

	public void setBirthDate(final String birthDate)
	{
		this.birthDate = birthDate;
	}

	public Boolean getActive()
	{
		return active;
	}

	public void setActive(final Boolean active)
	{
		this.active = active;
	}

	public String getSince()
	{
		return since;
	}

	public void setSince(final String since)
	{
		this.since = since;
	}

	public String getUntil()
	{
		return until;
	}

	public void setUntil(final String until)
	{
		this.until = until;
	}

	public String getEndReason()
	{
		return endReason;
	}

	public void setEndReason(final String endReason)
	{
		this.endReason = endReason;
	}
}
