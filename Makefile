REALPATH = $(shell which grealpath || which realpath)

GIT_REV := $(shell git rev-parse --short HEAD)
DOCKER_TAG := $(shell git branch --show-current)
DOCKER_CTX := $(shell git rev-parse --show-toplevel)
DOCKER_IMG := git.volgar.xyz/volgar1x/showhost
DOCKER_MARCH := linux/arm64/v8

.PHONY: all
all:
	cd "$(shell git rev-parse --show-toplevel)" && \
		bloop compile backend

.PHONY: publish_docker
publish_docker:
	docker buildx build \
		--push \
		--platform $(DOCKER_MARCH) \
		--tag $(DOCKER_IMG):$(GIT_REV) \
		--tag $(DOCKER_IMG):$(DOCKER_TAG) \
		-f "$(CURDIR)/Dockerfile" \
		"$(shell $(REALPATH) --relative-to=$(CURDIR) $(DOCKER_CTX))"

.PHONY: clean
clean:
	rm -rf target .bloop .bsp .metals
