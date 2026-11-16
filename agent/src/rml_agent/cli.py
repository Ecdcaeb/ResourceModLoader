from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
import sys
import uuid

import httpx

from .client import AgentClient, ClientError, Connection, run_message


def _connection_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--connection", help="Private JSON connection file returned by /rml agent start")
    parser.add_argument("--connect", help="Service URL; can override a connection file for an SSH tunnel")
    parser.add_argument("--session", help="Agent session UUID")
    parser.add_argument("--token-env", default="RML_AGENT_TOKEN", help="Environment variable containing this session's access token")


def _connection(args: argparse.Namespace) -> Connection:
    return Connection.load(file=args.connection, endpoint=args.connect, session=args.session,
                           token=None, token_env=args.token_env)


async def _status(connection: Connection) -> None:
    async with AgentClient(connection) as client:
        print(json.dumps(await client.status(), ensure_ascii=False, indent=2))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="rml-agent", description="Connect to the Agent hosted by your Minecraft server.")
    commands = parser.add_subparsers(dest="command", required=True)
    service = commands.add_parser("serve", help="Run the service (normally started by Minecraft)")
    service.add_argument("--config", type=Path, required=True)
    service.add_argument("--state-dir", type=Path, required=True)
    service.add_argument("--bridge", required=True)
    service.add_argument("--instance", required=True)
    service.add_argument("--ready-file", type=Path, required=True)
    terminal = commands.add_parser("tui", help="Attach a terminal; exiting leaves the Agent running")
    _connection_arguments(terminal)
    status = commands.add_parser("status", help="Read this session's current status")
    _connection_arguments(status)
    run = commands.add_parser("run", help="Submit one message and stream its durable results")
    _connection_arguments(run)
    run.add_argument("message")
    run.add_argument("--request-id", default=None, help="Reuse only to retrieve the same submission after an ambiguous response")
    run.add_argument("--json", action="store_true", help="Print structured events as JSON lines")
    args = parser.parse_args(argv)
    try:
        if args.command == "serve":
            from .service import serve
            serve(config_path=args.config.resolve(), state_dir=args.state_dir.resolve(), bridge_url=args.bridge,
                  instance_id=str(uuid.UUID(args.instance)), ready_file=args.ready_file.resolve())
        elif args.command == "tui":
            from .tui import run_terminal
            run_terminal(_connection(args))
        elif args.command == "status":
            asyncio.run(_status(_connection(args)))
        else:
            request_id = str(uuid.UUID(args.request_id)) if args.request_id else str(uuid.uuid4())
            return asyncio.run(run_message(_connection(args), args.message, request_id, args.json))
        return 0
    except KeyboardInterrupt:
        return 130
    except (ClientError, httpx.HTTPError, OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
