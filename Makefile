install:
	source .venv/bin/activate && \
	python3 -m pip install -r requirement.txt && \
	ansible-playbook ansible-playbook.yml --extra-vars="mode=deploy"

uninstall:
	source .venv/bin/activate && \
	python3 -m pip install -r requirement.txt && \
	ansible-playbook ansible-playbook.yml --extra-vars="mode=destroy"