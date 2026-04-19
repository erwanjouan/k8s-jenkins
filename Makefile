activate:
	source .venv/bin/activate && \
	python3 -m pip install -r requirement.txt

install: activate
	ansible-playbook ansible-playbook.yml --extra-vars="mode=deploy"

uninstall: activate
	ansible-playbook ansible-playbook.yml --extra-vars="mode=destroy"