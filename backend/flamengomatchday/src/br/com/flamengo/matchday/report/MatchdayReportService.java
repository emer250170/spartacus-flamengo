package br.com.flamengo.matchday.report;

import br.com.flamengo.matchday.model.MatchdayGameModel;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;


/**
 * Relatório de ingressos dos jogos (tela "Relatórios" da perspectiva Matchday
 * Flamengo no Backoffice).
 */
public interface MatchdayReportService
{
	String TYPE_FULL = "INTEIRA";
	String TYPE_HALF = "MEIA";
	String TYPE_MEMBER = "SOCIO";
	String TYPE_FREE = "GRATUIDADE";
	String TYPE_COURTESY = "CORTESIA";

	List<String> TICKET_TYPES = Arrays.asList(TYPE_FULL, TYPE_HALF, TYPE_MEMBER, TYPE_FREE, TYPE_COURTESY);

	List<String> SECTORS = Arrays.asList("NORTE", "SUL", "LESTE", "OESTE");

	/** Jogos que já foram abertos (à venda ou encerrados), do mais recente para o mais antigo */
	List<MatchdayGameModel> getGames();

	/** Relatório dos jogos informados, somados; vendas contadas até {@code now} */
	MatchdayReport getReport(Collection<MatchdayGameModel> games, Date now);
}
