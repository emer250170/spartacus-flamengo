package br.com.flamengo.matchday.occ;

import java.io.Serializable;


/** Corpo do POST /{baseSiteId}/flamengo/cadastro (os mesmos nomes do cadastro padrão, mais o CPF) */
public class CadastroWsDTO implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String firstName;
	private String lastName;
	private String uid;
	private String password;
	private String cpf;

	public String getFirstName()
	{
		return firstName;
	}

	public void setFirstName(final String firstName)
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName(final String lastName)
	{
		this.lastName = lastName;
	}

	public String getUid()
	{
		return uid;
	}

	public void setUid(final String uid)
	{
		this.uid = uid;
	}

	public String getPassword()
	{
		return password;
	}

	public void setPassword(final String password)
	{
		this.password = password;
	}

	public String getCpf()
	{
		return cpf;
	}

	public void setCpf(final String cpf)
	{
		this.cpf = cpf;
	}
}
