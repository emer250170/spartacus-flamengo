package br.com.flamengo.matchday.loja;

import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.core.model.product.ProductModel;

import java.util.List;


/**
 * Cadastro simples de produto da loja (assistente "Novo produto" e aba "Cadastro" do gestor da loja, 08/11).
 * <p>
 * O assistente grava no produto o que o gestor digitou; preço, estoque e fotos ficam "pendentes" no produto
 * (flaPrecoNovo, flaEstoqueNovo, flaFotoNova0..3) e, logo depois da gravação, numa linha de execução própria, viram a
 * linha de preço em BRL, o estoque no depósito da loja e os conjuntos de mídias com os tamanhos da loja; a busca é
 * atualizada e os pendentes voltam a vazio. Produto sem dado pendente não muda.
 * <p>
 * Chave {@code flamengo.loja.cadastro.ativo} (padrão true): desligada, os pendentes ficam gravados e nada mais acontece.
 */
public interface ProdutoCadastroService
{
	String ATIVO = "flamengo.loja.cadastro.ativo";

	String PRECO_NOVO = "flaPrecoNovo";
	String ESTOQUE_NOVO = "flaEstoqueNovo";
	String FOTO_NOVA = "flaFotoNova";

	boolean ativo();

	/** O usuário da sessão usa o cadastro simples (grupos de flamengo.loja.cadastro.grupos) */
	boolean usuarioDoCadastro();

	/** Valores iniciais de um produto novo do gestor: catálogo da loja (Online), aprovado e unidade "pieces" */
	void valoresIniciais(ProductModel product);

	/** Há preço, estoque ou foto pendente */
	boolean temPendente(ProductModel product);

	/** Aplica os pendentes numa linha de execução própria, depois que o produto estiver gravado */
	void aplicarDepoisDeGravar(ProductModel product);

	/** Aplica os pendentes agora (preço, estoque, fotos, busca) e limpa; devolve um resumo curto */
	String aplicar(ProductModel product);

	/** Preço de venda atual em BRL (linha sem grupo de cliente, quantidade 1), ou null */
	Double precoAtual(ProductModel product);

	/** Estoque disponível no depósito da loja, ou null */
	Integer estoqueAtual(ProductModel product);

	/** Categorias da loja que o gestor pode escolher, ordenadas pelo caminho */
	List<CategoryModel> categoriasDaLoja();

	/** Caminho da categoria a partir da raiz da loja (ex.: "Mantos › Jogo 1 › Masculino") */
	String caminho(CategoryModel category);

	/** Categoria da loja do produto (a primeira que não é técnica), ou null */
	CategoryModel categoriaDaLoja(ProductModel product);

	/** Troca a categoria da loja do produto, mantendo as categorias técnicas (tamanho, etc.) */
	void definirCategoriaDaLoja(ProductModel product, CategoryModel category);
}
