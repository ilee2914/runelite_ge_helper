package com.github.ilee2.gehelper;

import lombok.Data;

/**
 * Represents an item mapping entry from the OSRS Wiki /mapping endpoint.
 */
@Data
public class ItemMapping
{
	private int id;
	private String name;
	private String examine;
	private boolean members;
	private int lowalch;
	private int highalch;
	private int limit;
	private int value;
	private String icon;
}
