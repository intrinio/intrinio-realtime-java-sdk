FROM maven

RUN mkdir /intrinio
RUN mkdir /intrinio/target
RUN mkdir /intrinio/target/classes
RUN mkdir /intrinio/target/classes/intrinio

COPY . /intrinio

WORKDIR /intrinio

RUN mvn clean compile package install
RUN cp src/intrinio/config.json target/classes/intrinio/

CMD mvn exec:java -Dexec.mainClass="SampleApp.SampleApp"