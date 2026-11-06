package br.com.flamengo.matchday.personalization;

import java.util.ArrayList;
import java.util.List;


/** Personalização de uma linha de manto do carrinho ou do pedido. */
public class Personalization
{
	public int entryNumber;
	public String productCode;
	public String name;
	public String number;
	public boolean sponsor;
	public String patchCode;
	public String patchName;
	/** Linhas de preço (FLA-PERS-*) desta personalização */
	public List<Integer> addOnEntries = new ArrayList<>();
}
