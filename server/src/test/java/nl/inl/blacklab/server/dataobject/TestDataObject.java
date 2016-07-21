package nl.inl.blacklab.server.dataobject;

import org.junit.Assert;
import org.junit.Test;

public class TestDataObject {

	@Test
	public void dataObjFloat() {
		DataObject d = new DataObjectNumber(1.23);
		Assert.assertEquals("1.23", d.toString(DataFormat.JSON));
		Assert.assertEquals("1.23", d.toString(DataFormat.XML));
	}

	@Test
	public void dataObjInt() {
		DataObject d = new DataObjectNumber(1);
		Assert.assertEquals("1", d.toString(DataFormat.JSON));
		Assert.assertEquals("1", d.toString(DataFormat.XML));
	}

	@Test
	public void dataObjString() {
		DataObject d = new DataObjectString("\"quick\" fox");
		Assert.assertEquals("\"\\\"quick\\\" fox\"", d.toString(DataFormat.JSON));
		Assert.assertEquals("&quot;quick&quot; fox", d.toString(DataFormat.XML));
	}

	@Test
	public void dataObjList() {
		DataObjectList d = new DataObjectList("array-element");
		Assert.assertEquals("[\n]", d.toString(DataFormat.JSON));
		Assert.assertEquals("", d.toString(DataFormat.XML));
		d.add(new DataObjectNumber(5.43));
		d.add(new DataObjectString("a<b"));
		Assert.assertEquals("[\n  5.43,\n  \"a<b\"\n]", d.toString(DataFormat.JSON));
		Assert.assertEquals("  <array-element>5.43</array-element>\n  <array-element>a&lt;b</array-element>\n", d.toString(DataFormat.XML));
	}

	@Test
	public void dataObjMap() {
		DataObjectMapElement d = new DataObjectMapElement();
		Assert.assertEquals("{\n}", d.toString(DataFormat.JSON));
		Assert.assertEquals("", d.toString(DataFormat.XML));
		DataObjectList l = new DataObjectList(
			"bla",
			new DataObjectNumber(1),
			new DataObjectNumber(2),
			new DataObjectNumber(3)
		);
		d.put("quick", l);
		d.put("fox", new DataObjectString("lazy"));
		Assert.assertEquals("{\n  \"quick\": [\n    1,\n    2,\n    3\n  ],\n  \"fox\": \"lazy\"\n}", d.toString(DataFormat.JSON));
		Assert.assertEquals("  <quick>\n    <bla>1</bla>\n    <bla>2</bla>\n    <bla>3</bla>\n  </quick>\n  <fox>lazy</fox>\n", d.toString(DataFormat.XML));
	}

}
