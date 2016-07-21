package nl.inl.blacklab.server.search;

import java.io.File;

/** Index parameters */
class IndexParam {
	private File dir;

	private String pidField;

	private boolean mayViewContents;

	private boolean mayViewContentsSet;

	public IndexParam(File dir, String pidField, boolean mayViewContents) {
		super();
		this.dir = dir;
		this.pidField = pidField;
		this.mayViewContents = mayViewContents;
		mayViewContentsSet = true;
	}

	public IndexParam(File dir, String pidField) {
		super();
		this.dir = dir;
		this.pidField = pidField;
		mayViewContentsSet = false;
	}

	public IndexParam(File dir) {
		this(dir, "");
	}

	public File getDir() {
		return dir;
	}

	public String getPidField() {
		return pidField;
	}

	public boolean mayViewContents() {
		return mayViewContents;
	}

	public boolean mayViewContentsSpecified() {
		return mayViewContentsSet;
	}

	public void setPidField(String pidField) {
		this.pidField = pidField;
	}

	public void setMayViewContent(boolean b) {
		mayViewContents = false;
		mayViewContentsSet = true;
	}

}