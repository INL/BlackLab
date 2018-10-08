# Downloads

## Latest version

To download and build the most recent release of BlackLab, clone the repository and build it using Maven:

	git clone git://github.com/INL/BlackLab.git
	cd BlackLab
	mvn install
	
To use the latest development version, switch to the 'dev' branch:

	git clone git://github.com/INL/BlackLab.git
	cd BlackLab
	git checkout dev
	mvn install
	
If you're using Subversion, use:

	svn checkout https://github.com/INL/BlackLab/trunk BlackLab
	cd BlackLab
	mvn install

## Releases

See the [GitHub releases page](https://github.com/INL/BlackLab/releases/) for the complete list. This may also include development versions you can try out. If you're looking for the BlackLab library or commandline tools (i.e. not BlackLab Server), choose the version with libraries included.

Also see the [Change log](changelog.html).

<table>
	<tbody>
		<tr>
			<th>Version</th>
			<th colspan='3'>Downloads</th>
			<th>Description</th>
		</tr>
		<tr>
			<td>SNAPSHOT</td>
			<td></td>
			<td><a href='https://github.com/INL/BlackLab/archive/master.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/master.zip'>Source (zip)</a></td>
			<td>In-development version</td>
		</tr>
        <tr>
            <td>v1.7.2</td>
            <td><a href='https://github.com/INL/BlackLab/releases/download/v1.7.2/blacklab-1.7.2.jar'>Binary (jar)</a></td>
            <td><a href='https://github.com/INL/BlackLab/archive/v1.7.2.tar.gz'>Source (tgz)</a></td>
            <td><a href='https://github.com/INL/BlackLab/archive/v1.7.2.zip'>Source (zip)</a></td>
            <td>Bugfixes (Timeout, XPath match ordering)</td>
        </tr>
		<tr>
			<td>v1.7.1</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.7.1/blacklab-1.7.1.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.7.1.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.7.1.zip'>Source (zip)</a></td>
			<td>Made configuring input formats much easier, using a YAML (or JSON) file. Improved corpus structure information to allow better UI generation. Many other improvements.</td>
		</tr>
		<tr>
			<td>v1.6.0</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.6.0/blacklab-1.6.0.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.6.0.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.6.0.zip'>Source (zip)</a></td>
			<td>Added support for matching subqueries using forward index. Many other optimizations and fixes.</td>
		</tr>
		<tr>
			<td>v1.5.0</td>
			<td><a href='https://github.com/INL/BlackLab/releases/download/v1.5.0/blacklab-1.5.0.jar'>Binary (jar)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.5.0.tar.gz'>Source (tgz)</a></td>
			<td><a href='https://github.com/INL/BlackLab/archive/v1.5.0.zip'>Source (zip)</a></td>
			<td>Heavily refactored BlackLab Server to be more modular.</td>
		</tr>
	</tbody>
</table>
