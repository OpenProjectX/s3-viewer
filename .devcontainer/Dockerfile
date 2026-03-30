FROM mcr.microsoft.com/devcontainers/java:17-bullseye

# Install Node 20 + Yarn
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && npm install -g yarn \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
