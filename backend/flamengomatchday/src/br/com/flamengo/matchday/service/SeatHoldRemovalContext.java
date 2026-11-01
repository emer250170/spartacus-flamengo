package br.com.flamengo.matchday.service;

/**
 * Marca as remocoes de reserva feitas pelo proprio servico (venda, reserva
 * vencida, troca de poltrona, jogo encerrado). As que acontecem fora dele,
 * como apagar a reserva no Backoffice, nao tem a marca e disparam a limpeza
 * do carrinho e do estoque ({@link SeatHoldService#handleExternalRemoval}).
 */
public final class SeatHoldRemovalContext
{
	private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

	private SeatHoldRemovalContext()
	{
	}

	/** A remocao em andamento foi pedida pelo servico de reserva? */
	public static boolean isInternal()
	{
		final Integer depth = DEPTH.get();
		return depth != null && depth.intValue() > 0;
	}

	/** Executa com a marca de remocao interna (aceita chamadas aninhadas). */
	public static void runInternal(final Runnable body)
	{
		final Integer depth = DEPTH.get();
		DEPTH.set(Integer.valueOf(depth == null ? 1 : depth.intValue() + 1));
		try
		{
			body.run();
		}
		finally
		{
			final int left = DEPTH.get().intValue() - 1;
			if (left <= 0)
			{
				DEPTH.remove();
			}
			else
			{
				DEPTH.set(Integer.valueOf(left));
			}
		}
	}
}
