FROM maven

RUN mkdir /intrinio
RUN mkdir /intrinio/target
RUN mkdir /intrinio/target/classes
RUN mkdir /intrinio/target/classes/intrinio

COPY . /intrinio

WORKDIR /intrinio

RUN mvn clean compile package install
RUN cp src/intrinio/realtime/equities/config.json target/classes/intrinio/equities_config.json
RUN cp src/intrinio/realtime/options/config.json target/classes/intrinio/options_config.json
RUN apt-get update && apt-get install -y jq
RUN jq -s 'reduce .[] as $item ({}; . * $item)' target/classes/intrinio/equities_config.json target/classes/intrinio/options_config.json > target/classes/intrinio/config.json

CMD mvn exec:java -Dexec.mainClass="SampleApp.SampleApp"