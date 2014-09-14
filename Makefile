clean:
	mvn clean
	rm -rf package

run:
	mvn exec:java -Dexec.mainClass="jdbox.JdBox" -Dexec.args="mnt"

package:
	mvn clean package dependency:copy-dependencies
	rm -rf package
	mkdir -p package
	cp target/*.jar package
	cp run.sh package/
