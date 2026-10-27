package br.com.flamengo.matchday.attribute;

import br.com.flamengo.matchday.game.MatchdayGameService;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import de.hybris.platform.servicelayer.model.attribute.DynamicAttributeHandler;


/**
 * Contadores de poltronas do jogo, calculados na hora a partir do estoque:
 * TOTAL, AVAILABLE (livres, sem reserva) ou SOLD (vendidas).
 */
public class MatchdayGameSeatsAttributeHandler implements DynamicAttributeHandler<Integer, MatchdayGameModel>
{
	private MatchdayGameService matchdayGameService;
	private String mode = "TOTAL";

	@Override
	public Integer get(final MatchdayGameModel game)
	{
		if (game == null || game.getCode() == null)
		{
			return Integer.valueOf(0);
		}
		final int[] counts = matchdayGameService.countSeats(game.getCode());
		switch (mode)
		{
			case "AVAILABLE":
				return Integer.valueOf(counts[1]);
			case "SOLD":
				return Integer.valueOf(counts[2]);
			default:
				return Integer.valueOf(counts[0]);
		}
	}

	@Override
	public void set(final MatchdayGameModel game, final Integer value)
	{
		throw new UnsupportedOperationException("Calculado a partir do estoque");
	}

	public void setMatchdayGameService(final MatchdayGameService matchdayGameService)
	{
		this.matchdayGameService = matchdayGameService;
	}

	public void setMode(final String mode)
	{
		this.mode = mode;
	}
}
