package br.com.flamengo.matchday.game;

import br.com.flamengo.matchday.model.MatchdayGameModel;

import java.util.Map;


/**
 * Ciclo de vida de um jogo em casa: abrir (copiar o template do Maracana para o
 * produto do jogo), atualizar textos e precos, pausar a venda e encerrar.
 */
public interface MatchdayGameService
{
	String SECTOR_NORTE = "NORTE";
	String SECTOR_SUL = "SUL";
	String SECTOR_LESTE = "LESTE";
	String SECTOR_OESTE = "OESTE";

	/** Preco de cada setor no template (NORTE, SUL, LESTE, OESTE) */
	Map<String, Double> getTemplatePrices();

	/** JOGO-aaaammdd-ADV a partir da data e do adversario */
	String generateCode(MatchdayGameModel game);

	/** Cria (ou completa) o produto do jogo e as poltronas e liga game.product */
	String open(MatchdayGameModel game);

	/** Atualiza nome, descricao e precos do produto ja aberto (o estoque nao muda) */
	String update(MatchdayGameModel game);

	/** Liga (true) ou pausa (false) a venda do jogo ja aberto */
	String setOnSale(MatchdayGameModel game, boolean onSale);

	/** Tira o jogo da loja e apaga as poltronas nao vendidas */
	String close(MatchdayGameModel game);

	/** Poltronas do jogo: [total, livres, vendidas] */
	int[] countSeats(String gameCode);
}
