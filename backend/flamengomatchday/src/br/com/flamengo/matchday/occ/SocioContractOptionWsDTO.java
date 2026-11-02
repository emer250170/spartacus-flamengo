package br.com.flamengo.matchday.occ;

import java.io.Serializable;


/** Plano menor que o sócio pode agendar para a próxima vigência. */
public class SocioContractOptionWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String code;
	private String name;
	private Double monthlyPrice;

	public String getCode()
	{
		return code;
	}

	public void setCode(final String code)
	{
		this.code = code;
	}

	public String getName()
	{
		return name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public Double getMonthlyPrice()
	{
		return monthlyPrice;
	}

	public void setMonthlyPrice(final Double monthlyPrice)
	{
		this.monthlyPrice = monthlyPrice;
	}
}
