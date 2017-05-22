import Queue
import argparse
import json
import threading

import sys

from nmt import Suggestion
from nmt.opennmt import OpenNMTDecoder


class TranslationRequest:
    def __init__(self, _id, source, suggestions=None):
        self.id = _id
        self.source = source
        self.suggestions = suggestions if suggestions is not None else []

    @staticmethod
    def from_json_string(json_string):
        obj = json.loads(json_string)

        _id = int(obj['id'])
        source = obj['source'].split(' ')
        suggestions = []

        if 'suggestions' in obj:
            for sobj in obj['suggestions']:
                suggestion_source = sobj['source'].split(' ')
                suggestion_target = sobj['target'].split(' ')
                suggestion_score = float(sobj['score']) if 'score' in sobj else 0

                suggestions.append(Suggestion(suggestion_source, suggestion_target, suggestion_score))

        return TranslationRequest(_id, source, suggestions)


class TranslationResponse:
    def __init__(self, _id, translation=None, exception=None):
        self.id = _id
        self.translation = translation
        self.error_type = type(exception).__name__ if exception is not None else None
        self.error_message = str(exception) if exception is not None and str(exception) else None

    def to_json_string(self):
        jobj = {'id': self.id}

        if self.translation is not None:
            jobj['translation'] = ' '.join(self.translation)
        else:
            error = {'type': self.error_type}
            if self.error_message is not None:
                error['message'] = self.error_message
            jobj['error'] = error

        return json.dumps(jobj)


class MainController:
    POISON_PILL = '__POISON_PILL__'

    class ExecutorThread(threading.Thread):
        def __init__(self, decoder, in_queue, out_queue):
            super(MainController.ExecutorThread, self).__init__()
            self._decoder = decoder
            self._in = in_queue
            self._out = out_queue

        def run(self):
            while True:
                request = self._in.get()

                if isinstance(request, basestring) and request == MainController.POISON_PILL:
                    break

                try:
                    translation = self._decoder.translate(request.source, request.suggestions)
                    response = TranslationResponse(request.id, translation=translation)
                    print 'def MainController::run HERE response.to_json_string():', response.to_json_string()
                except BaseException as e:
                    response = TranslationResponse(request.id, exception=e)

                self._out.put(response)

    class PrinterThread(threading.Thread):
        def __init__(self, queue, stdout):
            super(MainController.PrinterThread, self).__init__()
            self._q = queue
            self._stdout = stdout

        def run(self):
            while True:
                response = self._q.get()

                if isinstance(response, basestring) and response == MainController.POISON_PILL:
                    break

                self._stdout.write(response.to_json_string())
                self._stdout.write('\n')
                self._stdout.flush()

    def __init__(self, decoder):
        self._decoder = decoder
        self._in = Queue.Queue()
        self._out = Queue.Queue()

        self._stdout = sys.stdout

        sys.stdout = sys.stderr

        self._workers = [MainController.ExecutorThread(self._decoder, self._in, self._out) for _ in
                         range(decoder.number_of_threads)]
        self._printer = MainController.PrinterThread(self._out, self._stdout)

    def serve_forever(self):
        try:
            # Start
            self._printer.start()
            for worker in self._workers:
                worker.start()

            # Serve
            while True:
                line = sys.stdin.readline()
                if not line:
                    break

                request = TranslationRequest.from_json_string(line)
                self._in.put(request)
        except KeyboardInterrupt:
            pass
        finally:
            self._close()

    def _close(self):
        with self._in.mutex:
            self._in.queue.clear()
        with self._out.mutex:
            self._out.queue.clear()

        for _ in self._workers:
            self._in.put(MainController.POISON_PILL)
        self._out.put(MainController.POISON_PILL)

        for worker in self._workers:
            worker.join()
        self._printer.join()


def run_main():
    parser = argparse.ArgumentParser(description='Run a forever-loop serving translation requests')
    parser.add_argument('model', metavar='MODEL', help='the path to the decoder model')

    args = parser.parse_args()

    decoder = OpenNMTDecoder(args.model)

    try:
        controller = MainController(decoder)
        controller.serve_forever()
    finally:
        decoder.close()


if __name__ == '__main__':
    run_main()





#############
##### An example of call
##### echo '{"id":1, "source":"hello and goodbye", "suggestions":[{"source": "A", "target": "a", "score":"0.1"},{"source": "B", "target": "b", "score":"0.2"}]}'  | python nmt_decoder modelAAA_acc_0.00_ppl_3634.55_e2.pt
#############


