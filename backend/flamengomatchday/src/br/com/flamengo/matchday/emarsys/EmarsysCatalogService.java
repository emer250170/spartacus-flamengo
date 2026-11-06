package br.com.flamengo.matchday.emarsys;

import java.util.Map;


/**
 * Catálogo de produtos no formato do Emarsys (Predict / Smart Insight).
 */
public interface EmarsysCatalogService
{
	/**
	 * CSV do catálogo (UTF-8, vírgula, cabeçalho na primeira linha), só com os produtos base do catálogo Online.
	 */
	String productCatalogCsv();

	/**
	 * Caminho de cada categoria como sai no campo {@code category} do CSV ("Masculino > Roupas > Camisas
	 * Diversas"), em português, pelo código da categoria. A loja usa no comando {@code category} do Web Extend.
	 */
	Map<String, String> categoryPaths();

	/** Esquece o que estava em cache (o próximo pedido gera de novo). */
	void clearCache();
}
