package br.com.flamengo.matchday.interceptor;

import br.com.flamengo.matchday.enums.MatchdayGameStatus;
import br.com.flamengo.matchday.game.MatchdayGameService;
import br.com.flamengo.matchday.model.MatchdayGameModel;
import de.hybris.platform.servicelayer.interceptor.InitDefaultsInterceptor;
import de.hybris.platform.servicelayer.interceptor.InterceptorContext;
import de.hybris.platform.servicelayer.interceptor.InterceptorException;
import de.hybris.platform.servicelayer.interceptor.PrepareInterceptor;
import de.hybris.platform.servicelayer.interceptor.RemoveInterceptor;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Liga a tela "Jogos" do Backoffice ao ciclo de vida do jogo.
 * <ul>
 * <li>novo jogo: preenche estadio, situacao "A venda" e os precos do template</li>
 * <li>salvar "A venda" sem produto: abre o jogo (cria produto e poltronas)</li>
 * <li>salvar mudancas de texto ou preco: atualiza o produto</li>
 * <li>"Rascunho" com produto: pausa a venda; voltar para "A venda": retoma</li>
 * <li>"Encerrado": tira da loja e apaga as poltronas nao vendidas</li>
 * </ul>
 * Erros chegam ao usuario como mensagem do Backoffice e o jogo nao e salvo.
 */
public class MatchdayGameInterceptor implements InitDefaultsInterceptor<MatchdayGameModel>,
		PrepareInterceptor<MatchdayGameModel>, RemoveInterceptor<MatchdayGameModel>
{
	private static final Logger LOG = LoggerFactory.getLogger(MatchdayGameInterceptor.class);

	private static final List<String> CONTENT = Arrays.asList(MatchdayGameModel.OPPONENT, MatchdayGameModel.KICKOFF,
			MatchdayGameModel.COMPETITION, MatchdayGameModel.VENUE, MatchdayGameModel.PRESALE, MatchdayGameModel.PRICENORTE,
			MatchdayGameModel.PRICESUL, MatchdayGameModel.PRICELESTE, MatchdayGameModel.PRICEOESTE, MatchdayGameModel.WAVE1START,
			MatchdayGameModel.WAVE2START, MatchdayGameModel.WAVE3START, MatchdayGameModel.GENERALSALESTART);

	/** Ondas em branco num jogo novo: 7, 6, 5 e 4 dias antes do jogo, às 10h */
	private static final int[] DEFAULT_DAYS_BEFORE = { 7, 6, 5, 4 };
	private static final int DEFAULT_HOUR = 10;

	private MatchdayGameService matchdayGameService;

	@Override
	public void onInitDefaults(final MatchdayGameModel game, final InterceptorContext ctx) throws InterceptorException
	{
		if (game.getStatus() == null)
		{
			game.setStatus(MatchdayGameStatus.ON_SALE);
		}
		if (game.getVenue() == null)
		{
			game.setVenue("Maracanã");
		}
		if (game.getPresale() == null)
		{
			game.setPresale(Boolean.FALSE);
		}
		if (game.getSimulatedSoldPercent() == null)
		{
			game.setSimulatedSoldPercent(Integer.valueOf(0));
		}
		try
		{
			final Map<String, Double> prices = matchdayGameService.getTemplatePrices();
			if (game.getPriceNorte() == null)
			{
				game.setPriceNorte(prices.get(MatchdayGameService.SECTOR_NORTE));
			}
			if (game.getPriceSul() == null)
			{
				game.setPriceSul(prices.get(MatchdayGameService.SECTOR_SUL));
			}
			if (game.getPriceLeste() == null)
			{
				game.setPriceLeste(prices.get(MatchdayGameService.SECTOR_LESTE));
			}
			if (game.getPriceOeste() == null)
			{
				game.setPriceOeste(prices.get(MatchdayGameService.SECTOR_OESTE));
			}
		}
		catch (final RuntimeException e)
		{
			LOG.warn("Preços do template indisponíveis: {}", e.getMessage());
		}
	}

	@Override
	public void onPrepare(final MatchdayGameModel game, final InterceptorContext ctx) throws InterceptorException
	{
		final boolean isNew = ctx.isNew(game);
		if (isNew)
		{
			defaultWaves(game);
		}
		validate(game);
		if (StringUtils.isBlank(game.getCode()))
		{
			game.setCode(matchdayGameService.generateCode(game));
		}
		else if (!isNew && game.getProduct() != null && ctx.isModified(game, MatchdayGameModel.CODE))
		{
			throw new InterceptorException("O código não pode mudar depois que o jogo foi aberto.");
		}

		final MatchdayGameStatus previous = isNew ? null
				: (MatchdayGameStatus) game.getItemModelContext().getOriginalValue(MatchdayGameModel.STATUS);
		final MatchdayGameStatus status = game.getStatus();
		if (previous == MatchdayGameStatus.CLOSED && status != MatchdayGameStatus.CLOSED)
		{
			throw new InterceptorException("Jogo encerrado não volta à venda. Cadastre um jogo novo.");
		}

		final StringBuilder messages = new StringBuilder();
		try
		{
			if (status == MatchdayGameStatus.ON_SALE)
			{
				if (game.getProduct() == null)
				{
					append(messages, matchdayGameService.open(game));
				}
				else if (!isNew)
				{
					if (previous == MatchdayGameStatus.DRAFT)
					{
						append(messages, matchdayGameService.setOnSale(game, true));
					}
					if (contentChanged(game, ctx))
					{
						append(messages, matchdayGameService.update(game));
					}
				}
			}
			else if (status == MatchdayGameStatus.DRAFT && game.getProduct() != null && !isNew)
			{
				if (previous == MatchdayGameStatus.ON_SALE)
				{
					append(messages, matchdayGameService.setOnSale(game, false));
				}
				if (contentChanged(game, ctx))
				{
					append(messages, matchdayGameService.update(game));
				}
			}
			else if (status == MatchdayGameStatus.CLOSED && previous != MatchdayGameStatus.CLOSED && game.getProduct() != null)
			{
				append(messages, matchdayGameService.close(game));
			}
		}
		catch (final RuntimeException e)
		{
			LOG.error("Falha ao processar o jogo " + game.getCode(), e);
			throw new InterceptorException(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), e);
		}
		if (messages.length() > 0)
		{
			final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
			format.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
			game.setStatusMessage(format.format(new Date()) + " — " + messages);
		}
	}

	@Override
	public void onRemove(final MatchdayGameModel game, final InterceptorContext ctx) throws InterceptorException
	{
		if (game.getStatus() != MatchdayGameStatus.CLOSED && game.getProduct() != null)
		{
			throw new InterceptorException(
					"Antes de apagar, mude a situação do jogo para Encerrado (isso tira o jogo da loja).");
		}
	}

	protected void validate(final MatchdayGameModel game) throws InterceptorException
	{
		if (StringUtils.isBlank(game.getOpponent()))
		{
			throw new InterceptorException("Informe o adversário.");
		}
		if (game.getKickoff() == null)
		{
			throw new InterceptorException("Informe a data e a hora do jogo.");
		}
		if (game.getStatus() == null)
		{
			throw new InterceptorException("Informe a situação do jogo.");
		}
		for (final Double price : Arrays.asList(game.getPriceNorte(), game.getPriceSul(), game.getPriceLeste(),
				game.getPriceOeste()))
		{
			if (price != null && price.doubleValue() < 0)
			{
				throw new InterceptorException("Preço não pode ser negativo.");
			}
		}
		validateWaves(game);
		final Integer percent = game.getSimulatedSoldPercent();
		if (percent != null && (percent.intValue() < 0 || percent.intValue() > 100))
		{
			throw new InterceptorException("A ocupação simulada vai de 0 a 100%.");
		}
	}

	/** Jogo novo com as 4 datas em branco recebe o calendário padrão */
	protected void defaultWaves(final MatchdayGameModel game)
	{
		if (game.getKickoff() == null || game.getWave1Start() != null || game.getWave2Start() != null
				|| game.getWave3Start() != null || game.getGeneralSaleStart() != null)
		{
			return;
		}
		game.setWave1Start(daysBefore(game.getKickoff(), DEFAULT_DAYS_BEFORE[0]));
		game.setWave2Start(daysBefore(game.getKickoff(), DEFAULT_DAYS_BEFORE[1]));
		game.setWave3Start(daysBefore(game.getKickoff(), DEFAULT_DAYS_BEFORE[2]));
		game.setGeneralSaleStart(daysBefore(game.getKickoff(), DEFAULT_DAYS_BEFORE[3]));
	}

	protected static Date daysBefore(final Date kickoff, final int days)
	{
		final Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
		c.setTime(kickoff);
		c.add(Calendar.DAY_OF_MONTH, -days);
		c.set(Calendar.HOUR_OF_DAY, DEFAULT_HOUR);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTime();
	}

	/** Todas as datas ou nenhuma; onda 1 < onda 2 < onda 3 < venda geral < jogo */
	protected void validateWaves(final MatchdayGameModel game) throws InterceptorException
	{
		final List<Date> dates = Arrays.asList(game.getWave1Start(), game.getWave2Start(), game.getWave3Start(),
				game.getGeneralSaleStart());
		final long filled = dates.stream().filter(java.util.Objects::nonNull).count();
		if (filled == 0)
		{
			return;
		}
		if (filled < dates.size())
		{
			throw new InterceptorException(
					"Preencha as quatro datas de venda (ondas 1, 2 e 3 e venda geral) ou deixe todas em branco.");
		}
		final List<String> names = Arrays.asList("onda 1", "onda 2", "onda 3", "venda geral");
		for (int i = 1; i < dates.size(); i++)
		{
			if (!dates.get(i - 1).before(dates.get(i)))
			{
				throw new InterceptorException("A " + names.get(i - 1) + " precisa abrir antes da " + names.get(i) + ".");
			}
		}
		if (!game.getGeneralSaleStart().before(game.getKickoff()))
		{
			throw new InterceptorException("A venda geral precisa abrir antes do jogo.");
		}
	}

	protected boolean contentChanged(final MatchdayGameModel game, final InterceptorContext ctx)
	{
		return CONTENT.stream().anyMatch(attribute -> ctx.isModified(game, attribute));
	}

	protected static void append(final StringBuilder messages, final String message)
	{
		if (StringUtils.isNotBlank(message))
		{
			if (messages.length() > 0)
			{
				messages.append(' ');
			}
			messages.append(message);
		}
	}

	public void setMatchdayGameService(final MatchdayGameService matchdayGameService)
	{
		this.matchdayGameService = matchdayGameService;
	}
}
