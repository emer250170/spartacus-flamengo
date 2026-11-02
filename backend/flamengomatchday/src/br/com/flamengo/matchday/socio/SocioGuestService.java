package br.com.flamengo.matchday.socio;

import br.com.flamengo.matchday.model.SocioGuestModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.Date;
import java.util.List;


/**
 * Convidados nominais do sócio-torcedor (item 3 do plano de evolução).
 * <p>
 * O convidado é contratado e cobrado na compra do plano (produto
 * NACAO-CONVIDADO-*); aqui o titular só dá nome a essas vagas. Regras:
 * <ul>
 * <li>limite = vagas de convidado contratadas no pedido do plano;</li>
 * <li>sócio com benefícios suspensos vê os convidados mas não inclui novos;</li>
 * <li>CPF válido (dígito verificador) e sem duplicidade entre convidados ativos;</li>
 * <li>tirar o convidado ou cancelar o plano só encerra o registro (fica o histórico).</li>
 * </ul>
 * Chave {@value #ENABLED_PROPERTY} (padrão true): false esconde o cadastro na loja e
 * recusa inclusões.
 */
public interface SocioGuestService
{
	String ENABLED_PROPERTY = "flamengo.socio.convidados.ativo";

	/** Motivo gravado quando o titular tira o convidado */
	String END_BY_OWNER = "Retirado pelo titular";
	/** Motivo gravado quando o cancelamento do plano é confirmado */
	String END_BY_CANCELLATION = "Cancelamento do plano";

	boolean isEnabled();

	/** Situação dos convidados do titular: plano, vagas, convidados ativos e encerrados */
	SocioGuestOverview overview(CustomerModel owner);

	/** Convidados do titular, os mais novos primeiro */
	List<SocioGuestModel> guests(CustomerModel owner, boolean onlyActive);

	/** Vagas de convidado contratadas no último pedido do plano (0 se não é sócio) */
	int contractedSlots(CustomerModel owner);

	/**
	 * Cadastra um convidado numa vaga livre e grava uma observação na linha do tempo do sócio.
	 *
	 * @throws SocioGuestException
	 *            com o motivo da recusa, em português, para mostrar ao torcedor
	 */
	SocioGuestModel add(CustomerModel owner, String name, String cpf, String email, String phone, Date birthDate)
			throws SocioGuestException;

	/**
	 * Encerra um convidado do titular (a vaga fica livre para outro).
	 *
	 * @param guestId
	 *           PK do convidado
	 */
	void remove(CustomerModel owner, String guestId) throws SocioGuestException;

	/**
	 * Encerra todos os convidados ativos do titular, SEM salvar (para uso dentro de
	 * interceptor, com registerElementFor).
	 *
	 * @return convidados alterados
	 */
	List<SocioGuestModel> endAll(CustomerModel owner, String reason);

	/** CPF com 11 dígitos e dígitos verificadores corretos (aceita pontos e traço) */
	static boolean isValidCpf(final String cpf)
	{
		final String d = digits(cpf);
		if (d.length() != 11 || d.chars().distinct().count() == 1)
		{
			return false;
		}
		for (int check = 9; check <= 10; check++)
		{
			int sum = 0;
			for (int i = 0; i < check; i++)
			{
				sum += (d.charAt(i) - '0') * (check + 1 - i);
			}
			final int dv = (sum * 10) % 11 % 10;
			if (dv != d.charAt(check) - '0')
			{
				return false;
			}
		}
		return true;
	}

	/** Só os dígitos ("123.456.789-09" -> "12345678909"); null vira "" */
	static String digits(final String value)
	{
		return value == null ? "" : value.replaceAll("\\D", "");
	}

	/** 12345678909 -> 123.456.789-09 */
	static String formatCpf(final String cpf)
	{
		final String d = digits(cpf);
		return d.length() != 11 ? d : d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
	}

	/** 12345678909 -> ***.456.789-** (para a linha do tempo) */
	static String maskCpf(final String cpf)
	{
		final String d = digits(cpf);
		return d.length() != 11 ? "***" : "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
	}
}
