package br.com.flamengo.matchday.personalization;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.CustomerModel;

import java.util.List;


/**
 * Personalização do manto na página do produto (06/11, mockup da loja oficial): nome e número,
 * kit de patrocínio e patch. A escolha fica na linha do manto (flaPersName, flaPersNumber,
 * flaPersSponsor, flaPersPatch); o preço de cada opção vem de uma linha própria do produto de
 * preço (FLA-PERS-*), marcada com flaPersOf = código do manto, com a mesma quantidade do manto.
 * Mudou a quantidade do manto ou ele saiu do carrinho: as linhas de preço acompanham
 * ({@link #syncAddOns}). Tudo passa para o pedido pelo clone padrão.
 * Chave flamengo.personalizacao.ativo (false = não oferece, como antes).
 */
public interface PersonalizationService
{
	String ACTIVE = "flamengo.personalizacao.ativo";
	String CATEGORIES = "flamengo.personalizacao.categorias";
	String NAME_NUMBER = "flamengo.personalizacao.nomeNumero";
	String SPONSOR = "flamengo.personalizacao.patrocinio";
	String PATCHES = "flamengo.personalizacao.patches";

	String ENTRY_NAME = "flaPersName";
	String ENTRY_NUMBER = "flaPersNumber";
	String ENTRY_SPONSOR = "flaPersSponsor";
	String ENTRY_PATCH = "flaPersPatch";
	String ENTRY_OF = "flaPersOf";

	boolean isActive();

	/** O produto (ou o produto base da variante) está numa das categorias personalizáveis */
	boolean isPersonalizable(String productCode);

	/** Opções com preço: nome e número, patrocínio e a lista de patches */
	PersonalizationOption nameNumberOption();

	PersonalizationOption sponsorOption();

	List<PersonalizationOption> patchOptions();

	/** Mantos personalizados do carrinho do torcedor */
	List<Personalization> list(CustomerModel owner, String cartCode) throws PersonalizationException;

	/** Mantos personalizados de um pedido do torcedor */
	List<Personalization> listOrder(CustomerModel owner, String orderCode) throws PersonalizationException;

	/**
	 * Grava a personalização da linha do manto (productCode) e ajusta as linhas de preço.
	 * Nome e número vazios, sem patrocínio e sem patch = tira a personalização.
	 */
	List<Personalization> set(CustomerModel owner, String cartCode, String productCode, String name, String number,
			boolean sponsor, String patchCode) throws PersonalizationException;

	/** Deixa as linhas de preço iguais ao que está personalizado; true se mudou algo */
	boolean syncAddOns(CartModel cart);

	/**
	 * A linha de preço vai sair do carrinho (o torcedor tirou "Personalização nome e número", por
	 * exemplo): a opção correspondente sai também do manto, senão a linha voltaria no ajuste.
	 */
	void dropOption(CartModel cart, int addOnEntryNumber);

	/** Personalizações de um carrinho ou pedido já carregado */
	List<Personalization> of(AbstractOrderModel order);
}
