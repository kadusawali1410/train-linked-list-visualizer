FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY TrainLinkedListVisualizer.java .
RUN javac TrainLinkedListVisualizer.java
EXPOSE 8080
CMD ["java", "TrainLinkedListVisualizer"]
